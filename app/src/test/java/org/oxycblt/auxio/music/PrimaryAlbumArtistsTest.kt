/*
 * Copyright (c) 2026 Auxio Project
 * PrimaryAlbumArtistsTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song

class PrimaryAlbumArtistsTest {
    private fun artist(): Artist {
        val id = mockk<Music.UID>()
        return mockk { every { uid } returns id }
    }

    private fun album(artists: List<Artist>, songs: List<Song>): Album = mockk {
        every { this@mockk.artists } returns artists
        every { this@mockk.songs } returns songs
    }

    @Test
    fun firstArtistOwnsCollaborativeAlbumAndAllItsSongs() {
        val peep = artist()
        val tray = artist()
        val song = mockk<Song>()
        val together = album(listOf(peep, tray), listOf(song))
        every { song.album } returns together
        every { song.artists } returns listOf(tray, peep)

        assertEquals(
            listOf(peep),
            PrimaryAlbumArtists.visible(listOf(peep, tray), listOf(together)),
        )
        assertEquals(listOf(together), PrimaryAlbumArtists.albums(peep, listOf(together)))
        assertEquals(emptyList<Album>(), PrimaryAlbumArtists.albums(tray, listOf(together)))
        assertEquals(listOf(song), PrimaryAlbumArtists.songs(peep, listOf(together)))
        assertEquals(listOf(peep), song.displayArtists(true))
        assertEquals(listOf(peep), together.displayArtists(true))
        assertEquals(listOf(tray, peep), song.displayArtists(false))
    }

    @Test
    fun secondaryArtistRemainsVisibleWhenFirstOnAnotherAlbum() {
        val peep = artist()
        val tray = artist()
        val together = album(listOf(peep, tray), emptyList())
        val solo = album(listOf(tray), emptyList())

        assertEquals(
            listOf(peep, tray),
            PrimaryAlbumArtists.visible(listOf(peep, tray), listOf(together, solo)),
        )
        assertEquals(listOf(solo), PrimaryAlbumArtists.albums(tray, listOf(together, solo)))
    }

    @Test
    fun shownFollowsTheSettingsLikeTheArtistsTab() {
        val peep = artist()
        val guest = artist()
        val together = album(listOf(peep, guest), emptyList())
        every { peep.explicitAlbums } returns listOf(together)
        every { guest.explicitAlbums } returns emptyList()
        val all = listOf(peep, guest)

        assertEquals(listOf(peep), PrimaryAlbumArtists.shown(all, listOf(together), true, false))
        assertEquals(listOf(peep), PrimaryAlbumArtists.shown(all, listOf(together), false, true))
        assertEquals(all, PrimaryAlbumArtists.shown(all, listOf(together), false, false))
    }
}
