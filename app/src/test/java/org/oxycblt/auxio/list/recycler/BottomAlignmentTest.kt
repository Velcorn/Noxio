/*
 * Copyright (c) 2026 Auxio Project
 * BottomAlignmentTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.list.recycler

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomAlignmentTest {
    @Test
    fun aShortListIsPushedDownByTheSpaceThatIsLeft() {
        val result = bottomAlignment(1000, paddingTop = 0, paddingBottomWithoutSpace = 0, 300)
        assertEquals(BottomAlignment(gap = 700, keepBottomSpace = false), result)
    }

    @Test
    fun thePaddingIsNotPartOfTheSpace() {
        val result = bottomAlignment(1000, paddingTop = 50, paddingBottomWithoutSpace = 150, 300)
        assertEquals(500, result.gap)
    }

    @Test
    fun aListThatFillsTheViewIsLeftAloneAndKeepsTheSpace() {
        val result = bottomAlignment(1000, 0, 0, contentHeight = 1000)
        assertEquals(BottomAlignment(gap = 0, keepBottomSpace = true), result)
    }

    @Test
    fun aListThatIsLongerThanTheViewHasNoGap() {
        val result = bottomAlignment(1000, 0, 0, contentHeight = 4000)
        assertEquals(BottomAlignment(gap = 0, keepBottomSpace = true), result)
    }

    @Test
    fun aViewTooSmallForItsPaddingNeverGivesANegativeGap() {
        val result = bottomAlignment(100, paddingTop = 80, paddingBottomWithoutSpace = 80, 10)
        assertEquals(0, result.gap)
    }
}
