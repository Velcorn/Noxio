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
    fun theDefaultTabsAreArtistsAlbumsSongsPlaylists() {
        val tabs = Tab.fromIntCode(Tab.SEQUENCE_DEFAULT)!!
        assertEquals(
            listOf(
                MusicType.ARTISTS,
                MusicType.ALBUMS,
                MusicType.SONGS,
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

    @Test
    fun aSavedSequenceWithAGenreTabIsReadWithoutIt() {
        // The old default: artists, albums, songs, genres, playlists.
        val old = 0b1010_1001_1000_1011_1100
        assertEquals(
            listOf(MusicType.ARTISTS, MusicType.ALBUMS, MusicType.SONGS, MusicType.PLAYLISTS),
            Tab.fromIntCode(old)!!.map { it.type },
        )
    }

    @Test
    fun aGenreTabIsNotSaved() {
        val tabs =
            arrayOf<Tab>(
                Tab.Visible(MusicType.ARTISTS),
                Tab.Visible(MusicType.GENRES),
                Tab.Visible(MusicType.ALBUMS),
                Tab.Visible(MusicType.SONGS),
                Tab.Invisible(MusicType.PLAYLISTS),
            )
        assertEquals(
            listOf(MusicType.ARTISTS, MusicType.ALBUMS, MusicType.SONGS, MusicType.PLAYLISTS),
            Tab.fromIntCode(Tab.toIntCode(tabs))!!.map { it.type },
        )
    }

    @Test
    fun hiddenTabsStayHidden() {
        val tabs = Tab.fromIntCode(Tab.SEQUENCE_DEFAULT)!!.toMutableList()
        tabs[2] = Tab.Invisible(tabs[2].type)
        val back = Tab.fromIntCode(Tab.toIntCode(tabs.toTypedArray()))!!
        assertEquals(listOf(true, true, false, true), back.map { it is Tab.Visible })
    }
}
