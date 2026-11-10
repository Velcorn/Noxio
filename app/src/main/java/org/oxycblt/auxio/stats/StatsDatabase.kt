/*
 * Copyright (c) 2026 Auxio Project
 * StatsDatabase.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.stats

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * One counted play of a song. Names are stored next to the UID on purpose: the UID changes when a
 * file is retagged or the library is rebuilt, and the history should outlive that.
 *
 * @param songUid The song's [org.oxycblt.musikr.Music.UID] as a string, at the time of the play.
 * @param title The song's title.
 * @param artists The song's artists, joined with [ARTIST_SEPARATOR].
 * @param album The song's album name.
 * @param startedAt When the song started, in milliseconds since the unix epoch.
 * @param listenedMs How long it was actually listened to, in milliseconds.
 * @param durationMs The length of the song, in milliseconds.
 * @param filePath The path of the file relative to its volume, for the CSV export. Empty if not
 *   known.
 * @param albumArtist The album artists of the song's album, joined with "; ". Empty if not known.
 * @param genre The first genre of the song. Empty if not known.
 * @param year The release year of the song. 0 if not known.
 * @param estimated True for plays that were made up from imported play counts: their time is a
 *   guess (spread over a week, or none at all), so they count for rankings and totals but not for
 *   what needs a real time, like days listened.
 */
@Entity(indices = [Index("startedAt")])
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songUid: String,
    val title: String,
    val artists: String,
    val album: String,
    val startedAt: Long,
    val listenedMs: Long,
    val durationMs: Long,
    @ColumnInfo(defaultValue = "''") val filePath: String = "",
    @ColumnInfo(defaultValue = "''") val albumArtist: String = "",
    @ColumnInfo(defaultValue = "''") val genre: String = "",
    @ColumnInfo(defaultValue = "0") val year: Int = 0,
    @ColumnInfo(defaultValue = "0") val estimated: Boolean = false,
) {
    /** Whether the time of this play is a guess, see [estimated]. Undated plays always are. */
    val hasGuessedTime: Boolean
        get() = estimated || startedAt == BASELINE_TIME

    companion object {
        /** Unit separator control character, which does not show up in artist names. */
        const val ARTIST_SEPARATOR = "\u001F"

        /**
         * The [startedAt] of plays from before Noxio, imported without a date. No week, month or
         * year contains it, so they only count towards "All time".
         */
        const val BASELINE_TIME = 0L
    }
}

/** Raw access to the listening history. */
@Database(entities = [PlayEvent::class], version = 3, exportSchema = false)
abstract class StatsDatabase : RoomDatabase() {
    abstract fun playEventDao(): PlayEventDao

    companion object {
        /** Added the file path, album artist, genre and year for the CSV export. */
        val MIGRATION_1_2 =
            Migration(1, 2) {
                it.execSQL("ALTER TABLE PlayEvent ADD COLUMN filePath TEXT NOT NULL DEFAULT ''")
                it.execSQL("ALTER TABLE PlayEvent ADD COLUMN albumArtist TEXT NOT NULL DEFAULT ''")
                it.execSQL("ALTER TABLE PlayEvent ADD COLUMN genre TEXT NOT NULL DEFAULT ''")
                it.execSQL("ALTER TABLE PlayEvent ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
            }

        /** Added the marker for plays whose time is a guess. Undated plays are such plays. */
        val MIGRATION_2_3 =
            Migration(2, 3) {
                it.execSQL("ALTER TABLE PlayEvent ADD COLUMN estimated INTEGER NOT NULL DEFAULT 0")
                it.execSQL("UPDATE PlayEvent SET estimated = 1 WHERE startedAt = 0")
            }
    }
}

@Dao
interface PlayEventDao {
    /** @return The id of the new row. */
    @Insert suspend fun insert(event: PlayEvent): Long

    @Query("UPDATE PlayEvent SET listenedMs = :listenedMs WHERE id = :id")
    suspend fun updateListened(id: Long, listenedMs: Long)

    @Insert suspend fun insertAll(events: List<PlayEvent>)

    /** How many undated plays from before Noxio there are. */
    @Query("SELECT COUNT(*) FROM PlayEvent WHERE startedAt = 0") suspend fun countBaseline(): Int

    /** Remove the undated plays from before Noxio, so a new import can replace them. */
    @Query("DELETE FROM PlayEvent WHERE startedAt = 0") suspend fun deleteBaseline()

    /** Remove the made up plays of a week, so a new import of that week can replace them. */
    @Query(
        "DELETE FROM PlayEvent WHERE estimated = 1 AND startedAt >= :fromMs AND startedAt < :toMs"
    )
    suspend fun deleteEstimatedBetween(fromMs: Long, toMs: Long)

    @Query("SELECT * FROM PlayEvent WHERE startedAt >= :fromMs AND startedAt < :toMs")
    suspend fun between(fromMs: Long, toMs: Long): List<PlayEvent>

    @Query("SELECT * FROM PlayEvent ORDER BY startedAt ASC") suspend fun all(): List<PlayEvent>

    @Query("DELETE FROM PlayEvent") suspend fun deleteAll()
}
