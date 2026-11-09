/*
 * Copyright (c) 2026 Auxio Project
 * BetterShuffleOrderTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.service

import androidx.media3.common.C
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** The order the player takes through the queue when shuffled. */
class BetterShuffleOrderTest {
    /** Walk the whole order from its first index to its end. */
    private fun BetterShuffleOrder.walk(): List<Int> {
        val order = mutableListOf<Int>()
        var index = firstIndex
        while (index != C.INDEX_UNSET) {
            order += index
            index = getNextIndex(index)
        }
        return order
    }

    private val order = BetterShuffleOrder(intArrayOf(2, 0, 3, 1))

    @Test
    fun aShuffleVisitsEverySongOnceAndStartsWhereAsked() {
        repeat(50) {
            val shuffled = BetterShuffleOrder(length = 20, startIndex = 7)
            val walked = shuffled.walk()
            assertEquals(7, walked.first())
            assertEquals((0 until 20).toList(), walked.sorted())
            assertEquals(20, shuffled.length)
        }
    }

    @Test
    fun aShuffleWithoutAStartCanStartAnywhere() {
        assertEquals((0 until 10).toList(), BetterShuffleOrder(10, -1).walk().sorted())
    }

    @Test
    fun theNextAndPreviousSongFollowTheOrder() {
        assertEquals(listOf(2, 0, 3, 1), order.walk())
        assertEquals(2, order.firstIndex)
        assertEquals(1, order.lastIndex)
        assertEquals(0, order.getNextIndex(2))
        assertEquals(3, order.getPreviousIndex(1))
        assertEquals(C.INDEX_UNSET, order.getNextIndex(1))
        assertEquals(C.INDEX_UNSET, order.getPreviousIndex(2))
    }

    @Test
    fun anEmptyOrderHasNeitherAFirstNorALastSong() {
        val empty = BetterShuffleOrder(0, -1)
        assertEquals(C.INDEX_UNSET, empty.firstIndex)
        assertEquals(C.INDEX_UNSET, empty.lastIndex)
        assertEquals(0, empty.length)
    }

    @Test
    fun songsInsertedInTheMiddlePlayRightAfterTheOneBeforeThem() {
        // Two songs are added after the song at index 1.
        val grown = order.cloneAndInsert(1, 2) as BetterShuffleOrder
        assertEquals(6, grown.length)
        assertEquals((0 until 6).toList(), grown.walk().sorted())
        val walked = grown.walk()
        assertEquals(walked.indexOf(1) + 1, walked.indexOf(2))
        assertEquals(walked.indexOf(1) + 2, walked.indexOf(3))
    }

    @Test
    fun songsAddedAtTheEndPlayLast() {
        val grown = order.cloneAndInsert(4, 2) as BetterShuffleOrder
        assertEquals(listOf(2, 0, 3, 1, 4, 5), grown.walk())
    }

    @Test
    fun insertingIntoAnEmptyOrderMakesAFreshShuffle() {
        val grown = BetterShuffleOrder(0, -1).cloneAndInsert(0, 5) as BetterShuffleOrder
        assertEquals((0 until 5).toList(), grown.walk().sorted())
    }

    @Test
    fun removedSongsLeaveTheRestInTheirOrderWithNewNumbers() {
        // Songs 1 and 2 go, so 3 becomes 1.
        val shrunk = order.cloneAndRemove(1, 3) as BetterShuffleOrder
        assertEquals(2, shrunk.length)
        assertEquals(listOf(0, 1), shrunk.walk())
    }

    @Test
    fun clearingGivesAnEmptyOrder() {
        assertEquals(0, (order.cloneAndClear() as BetterShuffleOrder).length)
    }

    @Test
    fun anOrderCanBeBuiltFromAnArray() {
        assertArrayEquals(
            intArrayOf(0, 1, 2),
            BetterShuffleOrder(intArrayOf(0, 1, 2)).walk().toIntArray(),
        )
    }
}
