package io.github.teamomuito.larpfm.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.teamomuito.larpfm.core.Larp
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
    /** Extra copies of this play made by LARPing it. Only filled in by [ScrobbleDb.recent]. */
    val larpCopies: Int = 0,
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

    /** Queues [scrobble] and returns its id. [larpOf] is the original's id when this is a LARP copy. */
    fun insert(scrobble: Scrobble, packageName: String?, larpOf: Long? = null): Long {
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
     * Queues up to [times] extra copies of scrobble [id], stopping at [Larp.MAX_TIMES] plays in
     * total. Returns how many copies were added.
     */
    fun addLarpCopies(id: Long, times: Int): Int {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val original = query("SELECT * FROM scrobbles WHERE id = $id AND larp_of IS NULL").firstOrNull() ?: return 0
            val existing = db.rawQuery("SELECT COUNT(*) FROM scrobbles WHERE larp_of = $id", null)
                .use { if (it.moveToFirst()) it.getInt(0) else 0 }
            val toAdd = minOf(times, Larp.MAX_TIMES - 1 - existing).coerceAtLeast(0)
            for (copy in existing + 1..existing + toAdd) {
                insert(Larp.copy(original.scrobble, copy), original.packageName, larpOf = id)
            }
            db.setTransactionSuccessful()
            return toAdd
        } finally {
            db.endTransaction()
        }
    }

    fun pending(limit: Int): List<ScrobbleEntry> = query(
        "SELECT * FROM scrobbles WHERE status = ${ScrobbleStatus.PENDING.id} ORDER BY timestamp LIMIT $limit",
    )

    /** The latest plays, newest first. LARP copies are folded into their original's [ScrobbleEntry.larpCopies]. */
    fun recent(limit: Int): List<ScrobbleEntry> = query(
        """
        SELECT s.*, (SELECT COUNT(*) FROM scrobbles c WHERE c.larp_of = s.id) AS larp_copies
        FROM scrobbles s
        WHERE s.larp_of IS NULL
        ORDER BY s.timestamp DESC, s.id DESC
        LIMIT $limit
        """.trimIndent(),
    )

    fun countPending(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM scrobbles WHERE status = ${ScrobbleStatus.PENDING.id}", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun setStatus(id: Long, status: ScrobbleStatus, message: String? = null) {
        val values = ContentValues().apply {
            put("status", status.id)
            put("message", message)
        }
        writableDatabase.update("scrobbles", values, "id = ?", arrayOf(id.toString()))
    }

    /** Drops old history, keeping everything that hasn't been sent yet. */
    fun prune(keep: Int) {
        writableDatabase.execSQL(
            """
            DELETE FROM scrobbles WHERE status != ${ScrobbleStatus.PENDING.id} AND id NOT IN (
                SELECT id FROM scrobbles WHERE status != ${ScrobbleStatus.PENDING.id}
                ORDER BY timestamp DESC LIMIT $keep
            )
            """.trimIndent(),
        )
    }

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
        val larpCopiesColumn = getColumnIndex("larp_copies")
        return ScrobbleEntry(
            id = long("id"),
            scrobble = Scrobble(track, long("timestamp")),
            packageName = string("package_name"),
            status = ScrobbleStatus.of(getInt(getColumnIndexOrThrow("status"))),
            message = string("message"),
            larpCopies = if (larpCopiesColumn >= 0) getInt(larpCopiesColumn) else 0,
        )
    }
}
