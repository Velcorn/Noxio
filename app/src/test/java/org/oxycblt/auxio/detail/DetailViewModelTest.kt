/*
 * Copyright (c) 2026 Auxio Project
 * DetailViewModelTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.detail

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.list.sort.Sort
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveLocksTheEditingSessionUntilTheWriteCompletes() =
        runTest(dispatcher) {
            val uid = mockk<Music.UID>()
            val songs = listOf(mockk<Song>(relaxed = true), mockk<Song>(relaxed = true))
            val playlist =
                mockk<Playlist>(relaxed = true) {
                    every { this@mockk.uid } returns uid
                    every { this@mockk.songs } returns songs
                }
            val generator =
                mockk<DetailGenerator>(relaxed = true) {
                    every { playlist(uid) } returns Detail(playlist, emptyList())
                }
            val factory =
                mockk<DetailGenerator.Factory> {
                    every { create(any()) } returns generator
                }
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val repository = mockk<MusicRepository>(relaxed = true)
            coEvery { repository.rewritePlaylist(playlist, songs) } coAnswers
                {
                    started.complete(Unit)
                    finish.await()
                }
            val model =
                DetailViewModel(
                    mockk(relaxed = true),
                    repository,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    factory,
                )
            model.setPlaylist(uid)
            model.startPlaylistEdit()
            model.savePlaylistEdit()
            // The guard is set synchronously, before the write coroutine starts.
            model.savePlaylistEdit()
            assertFalse(model.dropPlaylistEdit())
            runCurrent()
            started.await()
            model.startPlaylistEdit()
            model.removePlaylistSong(1)
            assertFalse(model.movePlaylistSongs(1, 2))
            val sort = Sort(Sort.Mode.ByName, Sort.Direction.ASCENDING)
            model.applyPlaylistSongSort(sort)
            model.sortPlaylist(sort)
            model.setPlaylist(mockk())
            assertEquals(songs, model.editedPlaylist.value)
            assertEquals(playlist, model.currentPlaylist.value)
            coVerify(exactly = 1) { repository.rewritePlaylist(any(), any()) }
            finish.complete(Unit)
            model.editedPlaylist.first { it == null }
            assertNull(model.editedPlaylist.value)
            model.startPlaylistEdit()
            assertEquals(songs, model.editedPlaylist.value)
            assertEquals(true, model.dropPlaylistEdit())
        }
}
