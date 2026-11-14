/*
 * Copyright (c) 2026 Auxio Project
 * ReplayGainStore.kt is part of Auxio.
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
 
package org.oxycblt.auxio.replaygain

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.oxycblt.musikr.Song

/**
 * The measured ReplayGain adjustment of one song, kept by Noxio so the audio file is not changed.
 */
@Entity
data class MeasuredGain(
    @PrimaryKey val songUid: String,
    /** The track adjustment in dB. */
    val gainDb: Float,
    val measuredAt: Long,
)

@Dao
interface MeasuredGainDao {
    @Upsert suspend fun save(gain: MeasuredGain)

    @Query("SELECT * FROM MeasuredGain") suspend fun all(): List<MeasuredGain>

    @Query("DELETE FROM MeasuredGain") suspend fun deleteAll()
}

@Database(entities = [MeasuredGain::class], version = 1, exportSchema = false)
abstract class ReplayGainDatabase : RoomDatabase() {
    abstract fun measuredGainDao(): MeasuredGainDao
}

/**
 * The measured ReplayGain values. They are loaded into memory once, as the audio processor asks for
 * one every time a song starts and must not wait for the database.
 */
@Singleton
class ReplayGainStore @Inject constructor(private val dao: MeasuredGainDao) {
    private val gains = ConcurrentHashMap<String, Float>()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            dao.all().forEach { gains.putIfAbsent(it.songUid, it.gainDb) }
        }
    }

    /** The measured track adjustment in dB for [song], or null if it has not been measured. */
    fun gainFor(song: Song): Float? = gains[song.uid.toString()]

    /** Whether the song has been measured. */
    fun isMeasured(song: Song) = gains.containsKey(song.uid.toString())

    /** How many songs have a measured value. */
    val size: Int
        get() = gains.size

    suspend fun save(song: Song, gainDb: Float) {
        val uid = song.uid.toString()
        gains[uid] = gainDb
        dao.save(MeasuredGain(uid, gainDb, System.currentTimeMillis()))
    }

    suspend fun clear() {
        gains.clear()
        dao.deleteAll()
    }
}
