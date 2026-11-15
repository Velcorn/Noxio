/*
 * Copyright (c) 2026 Auxio Project
 * ReplayGainStoreTest.kt is part of Auxio.
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

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Library
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReplayGainStoreTest {
    private class FakeDao : MeasuredGainDao {
        val saved = mutableMapOf<String, MeasuredGain>()

        override suspend fun save(gain: MeasuredGain) {
            saved[gain.songUid] = gain
        }

        override suspend fun all() = saved.values.toList()

        override suspend fun deleteAll() = saved.clear()
    }

    private fun uid(name: String) =
        mockk<Music.UID> { every { this@mockk.toString() } returns name }

    private fun song(name: String) = mockk<Song> { every { uid } returns uid(name) }

    private val a = song("a")
    private val b = song("b")
    private val album =
        mockk<Album> {
            every { uid } returns uid("album")
            every { songs } returns listOf(a, b)
        }
    private val music =
        mockk<MusicRepository>(relaxed = true) {
            every { library } returns mockk<Library> { every { albums } returns listOf(album) }
        }

    private fun loudness(lufs: Double, peak: Float = 0.5f) =
        SongLoudness(LoudnessHistogram().apply { repeat(100) { add(lufs) } }, peak)

    @Test
    fun anAlbumHasAGainOnceAllItsSongsAreMeasured() = runBlocking {
        val store = ReplayGainStore(FakeDao(), music)
        store.save(a, -2f, loudness(-20.0))
        store.updateAlbums()
        assertNull(store.albumGainFor(album))
        store.save(b, -2f, loudness(-20.0))
        store.updateAlbums()
        assertEquals(2f - 0.05f, store.albumGainFor(album)!!, 0.06f)
    }

    @Test
    fun aSongMeasuredWithoutAlbumDataIsNotComplete() = runBlocking {
        val store = ReplayGainStore(FakeDao(), music)
        store.save(a, -2f)
        store.save(b, -2f, loudness(-20.0))
        store.updateAlbums()
        assertTrue(store.isMeasured(a))
        assertFalse(store.isComplete(a))
        assertTrue(store.isComplete(b))
        assertNull(store.albumGainFor(album))
    }

    @Test
    fun clearingForgetsTheAlbumsToo() = runBlocking {
        val store = ReplayGainStore(FakeDao(), music)
        store.save(a, -2f, loudness(-20.0))
        store.save(b, -2f, loudness(-30.0))
        store.updateAlbums()
        assertNotNull(store.albumGainFor(album))
        store.clear()
        assertNull(store.albumGainFor(album))
        assertNull(store.gainFor(a))
        assertEquals(0, store.size)
    }

    @Test
    fun whatWasMeasuredBeforeIsLoadedAtTheStart() = runBlocking {
        val dao = FakeDao()
        dao.save(MeasuredGain("a", -3f, 0, 0.5f, loudness(-20.0).histogram.encode()))
        dao.save(MeasuredGain("b", -4f, 0))
        val store = ReplayGainStore(dao, music)
        store.updateAlbums()
        assertEquals(-3f, store.gainFor(a))
        assertTrue(store.isComplete(a))
        assertFalse(store.isComplete(b))
    }
}
