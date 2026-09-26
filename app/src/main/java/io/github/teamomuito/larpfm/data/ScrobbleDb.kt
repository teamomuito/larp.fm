package io.github.teamomuito.larpfm.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.teamomuito.larpfm.core.Scrobble
import io.github.teamomuito.larpfm.core.Track

enum class ScrobbleStatus(val id: Int) {
    PENDING(0),
    SENT(1),
    IGNORED(2),
    REJECTED(3),
    ;

    companion object {
        fun of(id: Int) = entries.firstOrNull { it.id == id } ?: PENDING
    }
}

data class ScrobbleEntry(
    val id: Long,
    val scrobble: Scrobble,
    val packageName: String?,
    val status: ScrobbleStatus,
    /** Why Last.fm ignored or rejected it. */
    val message: String?,
    /** Auto-LARP copies of this play. Only filled in by [ScrobbleDb.recent]. */
    val larpCopies: Int = 0,
    /** How many of [larpCopies] are still waiting for their time to come. Only filled in by [ScrobbleDb.recent]. */
    val queuedCopies: Int = 0,
) {
    val timesScrobbled: Int get() = 1 + larpCopies
}

/** Every scrobble the app has made: the pending queue plus a short history of what was sent. */
class ScrobbleDb(context: Context) : SQLiteOpenHelper(context, "scrobbles.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE scrobbles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                artist TEXT NOT NULL,
                title TEXT NOT NULL,
                album TEXT,
                album_artist TEXT,
                duration_ms INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                package_name TEXT,
                status INTEGER NOT NULL,
                message TEXT,
                larp_of INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX scrobbles_status ON scrobbles (status, timestamp)")
        db.execSQL("CREATE INDEX scrobbles_larp_of ON scrobbles (larp_of)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE scrobbles ADD COLUMN larp_of INTEGER")
            db.execSQL("CREATE INDEX scrobbles_larp_of ON scrobbles (larp_of)")
        }
    }

    /** Queues [scrobble] plus its auto-LARP [copies], and returns the original's id. */
    fun insert(scrobble: Scrobble, packageName: String?, copies: List<Scrobble> = emptyList()): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = insertOne(scrobble, packageName, larpOf = null)
            copies.forEach { insertOne(it, packageName, larpOf = id) }
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    private fun insertOne(scrobble: Scrobble, packageName: String?, larpOf: Long?): Long {
        val track = scrobble.track
        val values = ContentValues().apply {
            put("artist", track.artist)
            put("title", track.title)
            put("album", track.album)
            put("album_artist", track.albumArtist)
            put("duration_ms", track.durationMs)
            put("timestamp", scrobble.timestampSec)
            put("package_name", packageName)
            put("status", ScrobbleStatus.PENDING.id)
            put("larp_of", larpOf)
        }
        return writableDatabase.insert("scrobbles", null, values)
    }

    /**
     * What can be sent now, real plays first. A copy waits until its timestamp has come and
     * Last.fm has taken the original, and is left out entirely unless [includeCopies].
     */
    fun pending(limit: Int, nowSec: Long, includeCopies: Boolean): List<ScrobbleEntry> {
        val copies = if (!includeCopies) {
            "0"
        } else {
            """
            s.timestamp <= $nowSec AND
                EXISTS (SELECT 1 FROM scrobbles o WHERE o.id = s.larp_of AND o.status = ${ScrobbleStatus.SENT.id})
            """.trimIndent()
        }
        return query(
            """
            SELECT s.* FROM scrobbles s
            WHERE s.status = ${ScrobbleStatus.PENDING.id} AND (s.larp_of IS NULL OR ($copies))
            ORDER BY s.larp_of IS NOT NULL, s.timestamp
            LIMIT $limit
            """.trimIndent(),
        )
    }

    /** The latest plays, newest first. Auto-LARP copies are counted in their original's [ScrobbleEntry.larpCopies]. */
    fun recent(limit: Int): List<ScrobbleEntry> = query(
        """
        SELECT s.*,
            (SELECT COUNT(*) FROM scrobbles c WHERE c.larp_of = s.id) AS larp_copies,
            (SELECT COUNT(*) FROM scrobbles c WHERE c.larp_of = s.id AND c.status = ${ScrobbleStatus.PENDING.id}) AS queued_copies
        FROM scrobbles s
        WHERE s.larp_of IS NULL
        ORDER BY s.timestamp DESC, s.id DESC
        LIMIT $limit
        """.trimIndent(),
    )

    /** Scrobbles waiting to be sent, leaving out copies whose time hasn't come yet. */
    fun countPending(nowSec: Long): Int = count(
        "status = ${ScrobbleStatus.PENDING.id} AND (larp_of IS NULL OR timestamp <= $nowSec)",
    )

    /** Scrobbles Last.fm took with a timestamp from [sinceSec] on. */
    fun countSentSince(sinceSec: Long): Int = count("status = ${ScrobbleStatus.SENT.id} AND timestamp >= $sinceSec")

    /** When the earliest waiting copy is due, or null if there are none. */
    fun nextCopySec(): Long? =
        readableDatabase.rawQuery(
            "SELECT MIN(timestamp) FROM scrobbles WHERE status = ${ScrobbleStatus.PENDING.id} AND larp_of IS NOT NULL",
            null,
        ).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }

    /** Also cancels the waiting copies of a play Last.fm didn't take. */
    fun setStatus(id: Long, status: ScrobbleStatus, message: String? = null) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("status", status.id)
                put("message", message)
            }
            db.update("scrobbles", values, "id = ?", arrayOf(id.toString()))
            if (status == ScrobbleStatus.IGNORED || status == ScrobbleStatus.REJECTED) {
                val cancelled = ContentValues().apply {
                    put("status", ScrobbleStatus.IGNORED.id)
                    put("message", "Last.fm didn't take the original")
                }
                db.update(
                    "scrobbles",
                    cancelled,
                    "larp_of = ? AND status = ${ScrobbleStatus.PENDING.id}",
                    arrayOf(id.toString()),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Drops old history, keeping everything that hasn't been sent yet and the originals of waiting copies. */
    fun prune(keep: Int) {
        writableDatabase.execSQL(
            """
            DELETE FROM scrobbles WHERE status != ${ScrobbleStatus.PENDING.id} AND id NOT IN (
                SELECT id FROM scrobbles WHERE status != ${ScrobbleStatus.PENDING.id}
                ORDER BY timestamp DESC LIMIT $keep
            ) AND id NOT IN (
                SELECT larp_of FROM scrobbles WHERE status = ${ScrobbleStatus.PENDING.id} AND larp_of IS NOT NULL
            )
            """.trimIndent(),
        )
    }

    /** Cancels waiting copies whose original is gone or wasn't taken by Last.fm, so they don't wait forever. */
    fun cancelOrphanedCopies() {
        writableDatabase.execSQL(
            """
            UPDATE scrobbles SET status = ${ScrobbleStatus.IGNORED.id}, message = 'Last.fm didn''t take the original'
            WHERE status = ${ScrobbleStatus.PENDING.id} AND larp_of IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM scrobbles o WHERE o.id = scrobbles.larp_of
                    AND o.status IN (${ScrobbleStatus.PENDING.id}, ${ScrobbleStatus.SENT.id})
            )
            """.trimIndent(),
        )
    }

    private fun count(where: String): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM scrobbles WHERE $where", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun query(sql: String): List<ScrobbleEntry> =
        readableDatabase.rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toEntry())
            }
        }

    private fun Cursor.toEntry(): ScrobbleEntry {
        fun string(column: String): String? = getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }
        fun long(column: String): Long = getLong(getColumnIndexOrThrow(column))

        val track = Track(
            artist = string("artist").orEmpty(),
            title = string("title").orEmpty(),
            album = string("album"),
            albumArtist = string("album_artist"),
            durationMs = long("duration_ms"),
        )
        fun optionalInt(column: String): Int = getColumnIndex(column).let { if (it >= 0) getInt(it) else 0 }

        return ScrobbleEntry(
            id = long("id"),
            scrobble = Scrobble(track, long("timestamp")),
            packageName = string("package_name"),
            status = ScrobbleStatus.of(getInt(getColumnIndexOrThrow("status"))),
            message = string("message"),
            larpCopies = optionalInt("larp_copies"),
            queuedCopies = optionalInt("queued_copies"),
        )
    }
}
