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
import androidx.room.migration.Migration
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.musikr.Album
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
    /** The highest sample, for the album adjustment. Null if measured before Noxio 1.15.0. */
    val peak: Float? = null,
    /** The [LoudnessHistogram] of the song, for the album adjustment. Null like [peak]. */
    val blocks: ByteArray? = null,
) {
    /** What the album adjustment needs of this song, or null if it was measured without it. */
    fun loudness(): SongLoudness? {
        val histogram = blocks?.let(LoudnessHistogram::decode) ?: return null
        return SongLoudness(histogram, peak ?: return null)
    }

    override fun equals(other: Any?) =
        other is MeasuredGain &&
            songUid == other.songUid &&
            gainDb == other.gainDb &&
            measuredAt == other.measuredAt &&
            peak == other.peak &&
            blocks.contentEquals(other.blocks)

    override fun hashCode() = songUid.hashCode()
}

@Dao
interface MeasuredGainDao {
    @Upsert suspend fun save(gain: MeasuredGain)

    @Query("SELECT * FROM MeasuredGain") suspend fun all(): List<MeasuredGain>

    @Query("DELETE FROM MeasuredGain") suspend fun deleteAll()
}

@Database(entities = [MeasuredGain::class], version = 2, exportSchema = false)
abstract class ReplayGainDatabase : RoomDatabase() {
    abstract fun measuredGainDao(): MeasuredGainDao

    companion object {
        /** Noxio 1.15.0 keeps what an album adjustment needs. */
        val MIGRATION_1_2 =
            Migration(1, 2) {
                it.execSQL("ALTER TABLE MeasuredGain ADD COLUMN peak REAL")
                it.execSQL("ALTER TABLE MeasuredGain ADD COLUMN blocks BLOB")
            }
    }
}

/**
 * The measured ReplayGain values. They are loaded into memory once, as the audio processor asks for
 * one every time a song starts and must not wait for the database. The album adjustments are worked
 * out again whenever the library or the measured values change.
 */
@Singleton
class ReplayGainStore
@Inject
constructor(private val dao: MeasuredGainDao, private val music: MusicRepository) :
    MusicRepository.UpdateListener {
    private val gains = ConcurrentHashMap<String, Float>()
    /** The songs whose measurement can be part of an album adjustment. */
    private val complete = ConcurrentHashMap.newKeySet<String>()
    private val albumGains = ConcurrentHashMap<String, Float>()

    // One at a time, so the album adjustments are never worked out from half-loaded values.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + worker)

    init {
        scope.launch {
            dao.all().forEach {
                gains.putIfAbsent(it.songUid, it.gainDb)
                if (it.blocks != null && it.peak != null) complete.add(it.songUid)
            }
            withContext(Dispatchers.Main) { music.addUpdateListener(this@ReplayGainStore) }
        }
    }

    override fun onMusicChanges(changes: MusicRepository.Changes) {
        if (changes.deviceLibrary) scope.launch { updateAlbumsNow() }
    }

    /** The measured track adjustment in dB for [song], or null if it has not been measured. */
    fun gainFor(song: Song): Float? = gains[song.uid.toString()]

    /**
     * The measured album adjustment in dB for [album], or null if not all its songs are measured.
     */
    fun albumGainFor(album: Album): Float? = albumGains[album.uid.toString()]

    /** Whether the song has been measured. */
    fun isMeasured(song: Song) = gains.containsKey(song.uid.toString())

    /** Whether the song was measured with everything the album adjustment needs. */
    fun isComplete(song: Song) = complete.contains(song.uid.toString())

    /** How many songs have a measured value. */
    val size: Int
        get() = gains.size

    /** Everything measured, as it is in the database (for the backup). */
    suspend fun all(): Map<String, MeasuredGain> =
        withContext(worker) { dao.all().associateBy { it.songUid } }

    /** Keep what was measured of [song]. Call [updateAlbums] when a batch of songs is done. */
    suspend fun save(song: Song, gainDb: Float, loudness: SongLoudness? = null) {
        val uid = song.uid.toString()
        gains[uid] = gainDb
        if (loudness != null) complete.add(uid)
        dao.save(
            MeasuredGain(
                uid,
                gainDb,
                System.currentTimeMillis(),
                loudness?.peak,
                loudness?.histogram?.encode(),
            )
        )
    }

    /** Work out the album adjustments again, from what is measured now. */
    suspend fun updateAlbums() = withContext(worker) { updateAlbumsNow() }

    private suspend fun updateAlbumsNow() {
        val albums = music.library?.albums ?: return
        val measured = dao.all().associateBy { it.songUid }
        val result = HashMap<String, Float>()
        for (album in albums) {
            val songs = album.songs.map { measured[it.uid.toString()]?.loudness() }
            AlbumGain.of(songs)?.let { result[album.uid.toString()] = it }
        }
        albumGains.keys.retainAll(result.keys)
        albumGains.putAll(result)
    }

    suspend fun clear() {
        gains.clear()
        complete.clear()
        albumGains.clear()
        withContext(worker) { dao.deleteAll() }
    }
}
