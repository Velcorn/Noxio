/*
 * Copyright (c) 2023 Auxio Project
 * PersistenceRepository.kt is part of Auxio.
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

import androidx.core.net.toUri
import javax.inject.Inject
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.musikr.Library
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.SongFile
import timber.log.Timber as L

/**
 * Manages the persisted playback state in a structured manner.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
interface PersistenceRepository {
    /** Read the previously persisted [PlaybackStateManager.SavedState]. */
    suspend fun readState(): PlaybackStateManager.SavedState?

    /**
     * Noxio: read the persisted state before the library has loaded, with stand-in songs built from
     * the tag cache (see [MusicRepository.preview]). Null if nothing was saved, or if the saved
     * queue has no file records (saved by an older version).
     */
    suspend fun readEarlyState(): PlaybackStateManager.SavedState?

    /**
     * Persist a new [PlaybackStateManager.SavedState].
     *
     * @param state The [PlaybackStateManager.SavedState] to persist.
     */
    suspend fun saveState(state: PlaybackStateManager.SavedState?): Boolean

    /**
     * Persist only the position in the current song, in the state saved last.
     *
     * @param positionMs The position in milliseconds.
     */
    suspend fun savePosition(positionMs: Long): Boolean
}

class PersistenceRepositoryImpl
@Inject
constructor(
    private val playbackStateDao: PlaybackStateDao,
    private val queueDao: QueueDao,
    private val musicRepository: MusicRepository,
) : PersistenceRepository {

    override suspend fun readState(): PlaybackStateManager.SavedState? {
        val library = musicRepository.library?.takeIf { !it.empty() } ?: return null
        return readState(library)
    }

    override suspend fun readEarlyState(): PlaybackStateManager.SavedState? {
        val playbackState: PlaybackState
        val file: QueueFileItem
        try {
            playbackState = playbackStateDao.getState() ?: return null
            // Only the song that plays: building every song of a long queue would take as long
            // as loading the library. The rest of the queue follows with the library.
            val heapIndex =
                queueDao.getHeap().firstOrNull { it.uid == playbackState.songUid }?.id
                    ?: return null
            file = queueDao.getFile(heapIndex) ?: return null
        } catch (e: Exception) {
            L.e("Unable to read the playing song's file")
            L.e(e.stackTraceToString())
            return null
        }
        val library = musicRepository.preview(listOf(file.toSongFile())) ?: return null
        val song = library.findSong(playbackState.songUid) ?: return null
        return PlaybackStateManager.SavedState(
            positionMs = playbackState.positionMs,
            repeatMode = playbackState.repeatMode,
            parent = playbackState.parentUid?.let { library.findParent(it) },
            heap = listOf(song),
            shuffledMapping = emptyList(),
            index = 0,
            songUid = song.uid,
        )
    }

    private suspend fun readState(library: Library): PlaybackStateManager.SavedState? {
        val playbackState: PlaybackState
        val heapItems: List<QueueHeapItem>
        val mappingItems: List<QueueShuffledMappingItem>
        try {
            playbackState = playbackStateDao.getState() ?: return null
            heapItems = queueDao.getHeap()
            mappingItems = queueDao.getShuffledMapping()
        } catch (e: Exception) {
            L.e("Unable read playback state")
            L.e(e.stackTraceToString())
            return null
        }

        val heap = heapItems.map { library.findSong(it.uid) }
        val shuffledMapping = mappingItems.map { it.index }
        val parent = playbackState.parentUid?.let { library.findParent(it) }

        return PlaybackStateManager.SavedState(
            positionMs = playbackState.positionMs,
            repeatMode = playbackState.repeatMode,
            parent = parent,
            heap = heap,
            shuffledMapping = shuffledMapping,
            index = playbackState.index,
            songUid = playbackState.songUid,
        )
    }

    override suspend fun savePosition(positionMs: Long): Boolean =
        try {
            playbackStateDao.updatePosition(positionMs)
            true
        } catch (e: Exception) {
            L.e("Unable to save the position")
            L.e(e.stackTraceToString())
            false
        }

    override suspend fun saveState(state: PlaybackStateManager.SavedState?): Boolean {
        try {
            playbackStateDao.nukeState()
            queueDao.nukeHeap()
            queueDao.nukeShuffledMapping()
            queueDao.nukeFiles()
        } catch (e: Exception) {
            L.e("Unable to clear previous state")
            L.e(e.stackTraceToString())
            return false
        }

        L.d("Successfully cleared previous state")
        if (state != null) {
            // Transform saved state into raw state, which can then be written to the database.
            val playbackState =
                PlaybackState(
                    id = 0,
                    index = state.index,
                    positionMs = state.positionMs,
                    repeatMode = state.repeatMode,
                    songUid = state.songUid,
                    parentUid = state.parent?.uid,
                )

            // Convert the remaining queue information do their database-specific counterparts.
            val heap =
                state.heap.mapIndexed { i, song -> QueueHeapItem(i, requireNotNull(song).uid) }

            val shuffledMapping =
                state.shuffledMapping.mapIndexed { i, index -> QueueShuffledMappingItem(i, index) }

            val files =
                state.heap.mapIndexedNotNull { i, song ->
                    song?.let(SongFile::of)?.let { QueueFileItem.of(i, it) }
                }

            try {
                playbackStateDao.insertState(playbackState)
                queueDao.insertHeap(heap)
                queueDao.insertShuffledMapping(shuffledMapping)
                queueDao.insertFiles(files)
            } catch (e: Exception) {
                L.e("Unable to write new state")
                L.e(e.stackTraceToString())
                return false
            }

            L.d("Successfully wrote new state")
        }

        return true
    }
}

/** The album, artist, genre or playlist with [uid] in this library, or null. */
internal fun Library.findParent(uid: Music.UID): MusicParent? =
    findAlbum(uid) ?: findArtist(uid) ?: findGenre(uid) ?: findPlaylist(uid)

internal fun QueueFileItem.toSongFile() =
    SongFile(uri.toUri(), volume, path, mimeType, size, modifiedMs)

internal fun QueueFileItem.Companion.of(id: Int, file: SongFile) =
    QueueFileItem(
        id,
        file.uri.toString(),
        file.volume,
        file.path,
        file.mimeType,
        file.size,
        file.modifiedMs,
    )
