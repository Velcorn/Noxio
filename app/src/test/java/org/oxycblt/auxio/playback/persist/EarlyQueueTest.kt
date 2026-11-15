/*
 * Copyright (c) 2026 Auxio Project
 * EarlyQueueTest.kt is part of Auxio.
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

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.state.RepeatMode
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Library
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.SongFile
import org.oxycblt.musikr.fs.Components
import org.oxycblt.musikr.fs.Format
import org.oxycblt.musikr.fs.Path
import org.oxycblt.musikr.fs.Volume
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Noxio: the queue is saved with its files, and read back before the library has loaded. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EarlyQueueTest {
    private val database =
        Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                PersistenceDatabase::class.java,
            )
            .allowMainThreadQueries()
            .build()

    @After fun close() = database.close()

    private val volume =
        mockk<Volume.Internal> { every { mediaStoreName } returns "external_primary" }
    private val uids =
        List(3) { Music.UID.fromString("uas00000000-0000-0000-0000-00000000000$it")!! }

    private fun song(n: Int) =
        mockk<Song> {
            every { uid } returns uids[n]
            every { uri } returns Uri.parse("content://media/$n")
            every { path } returns Path(volume, Components.parseUnix("Music/$n.mp3"))
            every { format } returns Format.MPEG3
            every { size } returns 100L + n
            every { modifiedMs } returns 1_000L + n
        }

    private val songs = List(3, ::song)
    private val album =
        mockk<Album> {
            every { uid } returns Music.UID.fromString("uaa00000000-0000-0000-0000-00000000000a")!!
        }

    /** A library that has the given songs and the album. */
    private fun libraryOf(found: List<Song>) =
        mockk<Library> {
            every { this@mockk.songs } returns found
            every { findSong(any()) } answers { found.firstOrNull { it.uid == firstArg() } }
            every { findAlbum(any()) } answers { album.takeIf { it.uid == firstArg<Music.UID>() } }
            every { findArtist(any()) } returns null
            every { findGenre(any()) } returns null
            every { findPlaylist(any()) } returns null
        }

    private fun state() =
        PlaybackStateManager.SavedState(
            positionMs = 42_000,
            repeatMode = RepeatMode.ALL,
            parent = album,
            heap = songs,
            shuffledMapping = listOf(2, 0, 1),
            index = 1,
            songUid = uids[0],
        )

    private fun repository(preview: Library?, asked: io.mockk.CapturingSlot<List<SongFile>>) =
        PersistenceRepositoryImpl(
            database.playbackStateDao(),
            database.queueDao(),
            mockk<MusicRepository>(relaxed = true) {
                every { library } returns null
                coEvery { preview(capture(asked)) } returns preview
            },
        )

    @Test
    fun thePlayingSongIsReadBackFromItsFileBeforeTheLibrary() = runBlocking {
        val asked = slot<List<SongFile>>()
        val repository = repository(libraryOf(songs), asked)
        repository.saveState(state())
        val read = repository.readEarlyState()!!
        // Only the file of the playing song was asked for, not the whole queue.
        assertEquals(listOf("Music/0.mp3"), asked.captured.map { it.path })
        assertEquals("external_primary", asked.captured[0].volume)
        assertEquals(100L, asked.captured[0].size)
        assertEquals(listOf(songs[0]), read.heap)
        assertEquals(emptyList<Int>(), read.shuffledMapping)
        assertEquals(0, read.index)
        assertEquals(uids[0], read.songUid)
        assertEquals(42_000L, read.positionMs)
        assertEquals(RepeatMode.ALL, read.repeatMode)
        assertSame(album, read.parent)
    }

    @Test
    fun everyQueuedSongKeepsItsFile() = runBlocking {
        val repository = repository(libraryOf(songs), slot())
        repository.saveState(state())
        assertEquals(
            listOf("Music/0.mp3", "Music/1.mp3", "Music/2.mp3"),
            database.queueDao().getFiles().sortedBy { it.id }.map { it.path },
        )
    }

    @Test
    fun aSongMissingFromTheCacheWaitsForTheLibrary() = runBlocking {
        val repository = repository(libraryOf(songs.drop(1)), slot())
        repository.saveState(state())
        assertNull(repository.readEarlyState())
    }

    @Test
    fun withoutAPreviewThereIsNoEarlySong() = runBlocking {
        val repository = repository(null, slot())
        repository.saveState(state())
        assertNull(repository.readEarlyState())
    }

    @Test
    fun aQueueSavedWithoutFilesWaitsForTheLibrary() = runBlocking {
        val repository = repository(libraryOf(songs), slot())
        repository.saveState(state())
        database.queueDao().nukeFiles()
        assertNull(repository.readEarlyState())
    }

    @Test
    fun withNothingSavedThereIsNoEarlySong() = runBlocking {
        assertNull(repository(libraryOf(songs), slot()).readEarlyState())
    }

    @Test
    fun clearingTheStateClearsTheFiles() = runBlocking {
        val repository = repository(libraryOf(songs), slot())
        repository.saveState(state())
        repository.saveState(null)
        assertEquals(emptyList<QueueFileItem>(), database.queueDao().getFiles())
    }
}
