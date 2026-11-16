/*
 * Copyright (c) 2026 Auxio Project
 * PrimaryArtistPlaybackTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.state

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.oxycblt.auxio.list.ListSettings
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.playback.PlaybackSettings
import org.oxycblt.musikr.Song

class PrimaryArtistPlaybackTest {
    private val factory =
        PlaybackCommandFactoryImpl(
            mockk(relaxed = true),
            mockk<PlaybackSettings>(relaxed = true),
            mockk<ListSettings>(relaxed = true),
            mockk<MusicRepository>(relaxed = true),
        )

    @Test
    fun selectedSongStartsWithinPrimaryArtistQueue() {
        val first = mockk<Song>()
        val second = mockk<Song>()
        val command = factory.songFromSongs(second, listOf(first, second), ShuffleMode.OFF)

        assertEquals(second, command?.song)
        assertEquals(listOf(first, second), command?.queue)
        assertNull(command?.parent)
        assertEquals(false, command?.shuffled)
    }

    @Test
    fun songOutsideQueueCannotBePlayedFromIt() {
        assertNull(factory.songFromSongs(mockk(), listOf(mockk()), ShuffleMode.OFF))
    }
}
