/*
 * Copyright (c) 2026 Auxio Project
 * SortTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.list.sort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SortTest {
    private val modes =
        listOf(
            Sort.Mode.ByName,
            Sort.Mode.ByArtist,
            Sort.Mode.ByAlbum,
            Sort.Mode.ByDate,
            Sort.Mode.ByDuration,
            Sort.Mode.ByCount,
            Sort.Mode.ByDisc,
            Sort.Mode.ByTrack,
            Sort.Mode.ByDateAdded,
            Sort.Mode.ByCustom,
        )

    @Test
    fun everyModeHasItsOwnCode() {
        assertEquals(modes.size, modes.map { it.intCode }.toSet().size)
    }

    @Test
    fun everyModeCanBeReadBackFromItsCode() {
        for (mode in modes) {
            assertEquals(mode, Sort.Mode.fromIntCode(mode.intCode))
        }
    }

    @Test
    fun aSortSurvivesTheRoundTripInBothDirections() {
        for (mode in modes) {
            for (direction in Sort.Direction.values()) {
                val sort = Sort(mode, direction)
                assertEquals(sort, Sort.fromIntCode(sort.intCode))
            }
        }
    }

    @Test
    fun anUnknownCodeIsNotASort() {
        assertNull(Sort.Mode.fromIntCode(0x1234))
        assertNull(Sort.fromIntCode(0x1234 shl 1))
    }

    @Test
    fun customOrderDoesNotChangeTheOrder() {
        // It is only the way to start moving songs by hand, it must not sort anything.
        assertEquals(
            emptyList<Any>(),
            Sort(Sort.Mode.ByCustom, Sort.Direction.ASCENDING).songs(emptyList()),
        )
    }
}
