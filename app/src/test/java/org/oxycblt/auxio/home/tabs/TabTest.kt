/*
 * Copyright (c) 2026 Auxio Project
 * TabTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.home.tabs

import org.junit.Assert.assertEquals
import org.junit.Test
import org.oxycblt.auxio.music.MusicType

class TabTest {
    @Test
    fun theDefaultTabsAreArtistsAlbumsSongsGenresPlaylists() {
        val tabs = Tab.fromIntCode(Tab.SEQUENCE_DEFAULT)!!
        assertEquals(
            listOf(
                MusicType.ARTISTS,
                MusicType.ALBUMS,
                MusicType.SONGS,
                MusicType.GENRES,
                MusicType.PLAYLISTS,
            ),
            tabs.map { it.type },
        )
    }

    @Test
    fun allTheDefaultTabsAreVisible() {
        assertEquals(true, Tab.fromIntCode(Tab.SEQUENCE_DEFAULT)!!.all { it is Tab.Visible })
    }

    @Test
    fun aTabSequenceSurvivesAConversionToACodeAndBack() {
        val tabs = Tab.fromIntCode(Tab.SEQUENCE_DEFAULT)!!
        assertEquals(Tab.SEQUENCE_DEFAULT, Tab.toIntCode(tabs))
    }
}
