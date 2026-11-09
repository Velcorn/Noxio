/*
 * Copyright (c) 2026 Auxio Project
 * SearchVisibleTypesTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.search

import org.junit.Assert.assertEquals
import org.junit.Test
import org.oxycblt.auxio.home.tabs.Tab
import org.oxycblt.auxio.music.MusicType

class SearchVisibleTypesTest {
    @Test
    fun `only types with a visible tab are searched`() {
        val tabs =
            arrayOf<Tab>(
                Tab.Visible(MusicType.SONGS),
                Tab.Visible(MusicType.ALBUMS),
                Tab.Visible(MusicType.ARTISTS),
                Tab.Invisible(MusicType.GENRES),
                Tab.Visible(MusicType.PLAYLISTS),
            )
        assertEquals(
            setOf(MusicType.SONGS, MusicType.ALBUMS, MusicType.ARTISTS, MusicType.PLAYLISTS),
            visibleSearchTypes(tabs),
        )
    }

    @Test
    fun `no visible tabs gives no types`() {
        assertEquals(
            emptySet<MusicType>(),
            visibleSearchTypes(arrayOf(Tab.Invisible(MusicType.SONGS))),
        )
    }
}
