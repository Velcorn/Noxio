/*
 * Copyright (c) 2023 Auxio Project
 * PersistenceDatabase.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.persist

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import org.oxycblt.auxio.playback.state.RepeatMode
import org.oxycblt.musikr.Music

/**
 * Provides raw access to the database storing the persisted playback state.
 *
 * @author Alexander Capehart
 */
@Database(
    entities =
        [
            PlaybackState::class,
            QueueHeapItem::class,
            QueueShuffledMappingItem::class,
            QueueFileItem::class,
        ],
    version = 39,
    exportSchema = false,
)
@TypeConverters(Music.UID.TypeConverters::class)
abstract class PersistenceDatabase : RoomDatabase() {
    /**
     * Get the current [PlaybackStateDao].
     *
     * @return A [PlaybackStateDao] providing control of the database's playback state tables.
     */
    abstract fun playbackStateDao(): PlaybackStateDao

    /**
     * Get the current [QueueDao].
     *
     * @return A [QueueDao] providing control of the database's queue tables.
     */
    abstract fun queueDao(): QueueDao

    companion object {
        val MIGRATION_27_32 =
            Migration(27, 32) {
                // Switched from custom names to just letting room pick the names
                it.execSQL("ALTER TABLE playback_state RENAME TO PlaybackState")
                it.execSQL("ALTER TABLE queue_heap RENAME TO QueueHeapItem")
                it.execSQL("ALTER TABLE queue_mapping RENAME TO QueueMappingItem")
            }

        /** Noxio: where the queued songs' files are, so the queue can play before the library. */
        val MIGRATION_38_39 =
            Migration(38, 39) {
                it.execSQL(
                    "CREATE TABLE IF NOT EXISTS `QueueFileItem` (`id` INTEGER NOT NULL, " +
                        "`uri` TEXT NOT NULL, `volume` TEXT NOT NULL, `path` TEXT NOT NULL, " +
                        "`mimeType` TEXT NOT NULL, `size` INTEGER NOT NULL, " +
                        "`modifiedMs` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
    }
}

/**
 * Provides control of the persisted playback state table.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@Dao
interface PlaybackStateDao {
    /**
     * Get the previously persisted [PlaybackState].
     *
     * @return The previously persisted [PlaybackState], or null if one was not present.
     */
    @Query("SELECT * FROM PlaybackState WHERE id = 0") suspend fun getState(): PlaybackState?

    /**
     * Change only the position in the persisted [PlaybackState], which is cheap enough to do every
     * few seconds. Does nothing if no state was persisted yet.
     *
     * @param positionMs The new position in the current song, in milliseconds.
     */
    @Query("UPDATE PlaybackState SET positionMs = :positionMs WHERE id = 0")
    suspend fun updatePosition(positionMs: Long)

    /** Delete any previously persisted [PlaybackState]s. */
    @Query("DELETE FROM PlaybackState") suspend fun nukeState()

    /**
     * Insert a new [PlaybackState] into the database.
     *
     * @param state The [PlaybackState] to insert.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertState(state: PlaybackState)
}

/**
 * Provides control of the persisted queue state tables.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@Dao
interface QueueDao {
    /**
     * Get the previously persisted queue heap.
     *
     * @return A list of persisted [QueueHeapItem]s wrapping each heap item.
     */
    @Query("SELECT * FROM QueueHeapItem") suspend fun getHeap(): List<QueueHeapItem>

    /**
     * Get the previously persisted queue mapping.
     *
     * @return A list of persisted [QueueShuffledMappingItem]s wrapping each heap item.
     */
    @Query("SELECT * FROM QueueShuffledMappingItem")
    suspend fun getShuffledMapping(): List<QueueShuffledMappingItem>

    /** Noxio: where the files of the heap are, by heap index. */
    @Query("SELECT * FROM QueueFileItem") suspend fun getFiles(): List<QueueFileItem>

    @Query("SELECT * FROM QueueFileItem WHERE id = :id")
    suspend fun getFile(id: Int): QueueFileItem?

    @Query("DELETE FROM QueueFileItem") suspend fun nukeFiles()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFiles(files: List<QueueFileItem>)

    /** Delete any previously persisted queue heap entries. */
    @Query("DELETE FROM QueueHeapItem") suspend fun nukeHeap()

    /** Delete any previously persisted queue mapping entries. */
    @Query("DELETE FROM QueueShuffledMappingItem") suspend fun nukeShuffledMapping()

    /**
     * Insert new heap entries into the database.
     *
     * @param heap The list of wrapped [QueueHeapItem]s to insert.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertHeap(heap: List<QueueHeapItem>)

    /**
     * Insert new mapping entries into the database.
     *
     * @param mapping The list of wrapped [QueueShuffledMappingItem] to insert.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertShuffledMapping(mapping: List<QueueShuffledMappingItem>)
}

// TODO: Figure out how to get RepeatMode to map to an int instead of a string
@Entity
data class PlaybackState(
    @PrimaryKey val id: Int,
    val index: Int,
    val positionMs: Long,
    val repeatMode: RepeatMode,
    val songUid: Music.UID,
    val parentUid: Music.UID?,
)

@Entity data class QueueHeapItem(@PrimaryKey val id: Int, val uid: Music.UID)

@Entity data class QueueShuffledMappingItem(@PrimaryKey val id: Int, val index: Int)

/** Noxio: where the file of the heap item [id] is, see [org.oxycblt.musikr.SongFile]. */
@Entity
data class QueueFileItem(
    @PrimaryKey val id: Int,
    val uri: String,
    val volume: String,
    val path: String,
    val mimeType: String,
    val size: Long,
    val modifiedMs: Long,
) {
    companion object
}
