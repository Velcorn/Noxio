/*
 * Copyright (c) 2026 Auxio Project
 * DiscGroupsTest.kt is part of Auxio.
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

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscGroupsTest {
    private data class S(val name: String, val disc: Int?)

    @Test
    fun songsByNameStayInTheirDiscAndTheDiscsInOrder() {
        // Sorted by name, the first song is on disc 2, but disc 1 still comes first.
        val byName = listOf(S("A", 2), S("B", 1), S("C", 2), S("D", 1))
        val groups = DiscGroups.group(byName, descending = false) { it.disc }
        assertEquals(listOf(1, 2), groups.keys.toList())
        assertEquals(listOf("B", "D"), groups.getValue(1).map { it.name })
        assertEquals(listOf("A", "C"), groups.getValue(2).map { it.name })
    }

    @Test
    fun aDescendingSortReversesTheDiscsAndSongsWithoutADiscComeFirst() {
        val songs = listOf(S("A", 1), S("B", null), S("C", 3))
        assertEquals(
            listOf(null, 1, 3),
            DiscGroups.group(songs, descending = false) { it.disc }.keys.toList(),
        )
        assertEquals(
            listOf(3, 1, null),
            DiscGroups.group(songs, descending = true) { it.disc }.keys.toList(),
        )
    }
}
