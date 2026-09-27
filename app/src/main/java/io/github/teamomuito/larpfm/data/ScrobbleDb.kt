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
    /** Why the site ignored or rejected it. */
    val message: String?,
)

/** Every scrobble the app has made: the pending queue plus a short history of what was sent. */
class ScrobbleDb(context: Context) : SQLiteOpenHelper(context, "scrobbles.db", null, 3) {

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
                message TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX scrobbles_status ON scrobbles (status, timestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versions 2 and 3: larp_of marked copies made by the LARP features, which have been removed.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE scrobbles ADD COLUMN larp_of INTEGER")
        }
        if (oldVersion < 3) {
            // Unsent copies, some queued for the future, would only be rejected now.
            db.execSQL("DELETE FROM scrobbles WHERE larp_of IS NOT NULL AND status = ${ScrobbleStatus.PENDING.id}")
        }
    }

    /** Queues [scrobble] and returns its id. */
    fun insert(scrobble: Scrobble, packageName: String?): Long {
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
        }
        return writableDatabase.insert("scrobbles", null, values)
    }

    fun pending(limit: Int): List<ScrobbleEntry> = query(
        "SELECT * FROM scrobbles WHERE status = ${ScrobbleStatus.PENDING.id} ORDER BY timestamp LIMIT $limit",
    )

    /** The latest scrobbles, newest first. */
    fun recent(limit: Int): List<ScrobbleEntry> =
        query("SELECT * FROM scrobbles ORDER BY timestamp DESC, id DESC LIMIT $limit")

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
        return ScrobbleEntry(
            id = long("id"),
            scrobble = Scrobble(track, long("timestamp")),
            packageName = string("package_name"),
            status = ScrobbleStatus.of(getInt(getColumnIndexOrThrow("status"))),
            message = string("message"),
        )
    }
}
