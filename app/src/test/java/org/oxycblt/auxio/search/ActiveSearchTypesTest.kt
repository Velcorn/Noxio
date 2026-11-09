/*
 * Copyright (c) 2026 Auxio Project
 * ActiveSearchTypesTest.kt is part of Auxio.
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
import org.oxycblt.auxio.music.MusicType

class ActiveSearchTypesTest {
    private val visible = setOf(MusicType.SONGS, MusicType.ALBUMS, MusicType.ARTISTS)

    @Test
    fun nothingPickedSearchesEverythingThatHasATab() {
        assertEquals(visible, activeSearchTypes(emptySet(), visible))
    }

    @Test
    fun aPickedTypeIsTheOnlyOneSearched() {
        assertEquals(
            setOf(MusicType.ALBUMS),
            activeSearchTypes(setOf(MusicType.ALBUMS), visible),
        )
    }

    @Test
    fun aPickedTypeWithoutATabIsDropped() {
        assertEquals(
            setOf(MusicType.SONGS),
            activeSearchTypes(setOf(MusicType.SONGS, MusicType.GENRES), visible),
        )
    }

    @Test
    fun ifOnlyHiddenTypesWerePickedEverythingThatHasATabIsSearched() {
        assertEquals(visible, activeSearchTypes(setOf(MusicType.GENRES), visible))
    }
}
