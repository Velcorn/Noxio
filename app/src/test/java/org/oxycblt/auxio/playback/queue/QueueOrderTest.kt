/*
 * Copyright (c) 2026 Auxio Project
 * QueueOrderTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.queue

import io.mockk.every
import io.mockk.mockk
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.tag.interpret.Naming

/** The orders and the filter of the queue. */
class QueueOrderTest {
    private val naming = Naming.simple()

    private fun song(title: String, artist: String = "Artist", album: String = "Album"): Song {
        val artistMock = mockk<Artist> { every { name } returns naming.name(artist, null) }
        val albumMock = mockk<Album> { every { name } returns naming.name(album, null) }
        return mockk {
            every { name } returns naming.name(title, null)
            every { artists } returns listOf(artistMock)
            every { this@mockk.album } returns albumMock
        }
    }

    private val a = song("Alison", "Slowdive", "Souvlaki")
    private val b = song("Blue Hour", "Luna Vale", "Nocturne")
    private val c = song("Cherry", "Slowdive", "Pygmalion")

    @Test
    fun theOrderOfASortedQueueIsGivenByPosition() {
        // The queue is c, a, b and sorted it is a, b, c.
        assertEquals(listOf(1, 2, 0), QueueOrder.matching(listOf(c, a, b), listOf(a, b, c)))
    }

    @Test
    fun anUnchangedQueueKeepsItsOrder() {
        assertEquals(listOf(0, 1, 2), QueueOrder.matching(listOf(a, b, c), listOf(a, b, c)))
    }

    @Test
    fun aSongThatIsThereSeveralTimesTakesItsPositionsInTurn() {
        // a, b, a, c sorted by name is a, a, b, c.
        assertEquals(
            listOf(0, 2, 1, 3),
            QueueOrder.matching(listOf(a, b, a, c), listOf(a, a, b, c)),
        )
    }

    @Test
    fun aSortedQueueMustHaveTheSongsOfTheQueue() {
        assertThrows(IllegalArgumentException::class.java) {
            QueueOrder.matching(listOf(a, b), listOf(a))
        }
        assertThrows(IllegalArgumentException::class.java) {
            QueueOrder.matching(listOf(a, b), listOf(a, c))
        }
    }

    @Test
    fun aRandomOrderHasEveryPositionOnce() {
        val order = QueueOrder.random(50, Random(7))
        assertEquals((0 until 50).toList(), order.sorted())
        // And it is not the order it started in.
        assertEquals(false, order == (0 until 50).toList())
    }

    @Test
    fun aRandomOrderOfNothingIsNothing() {
        assertEquals(emptyList<Int>(), QueueOrder.random(0))
    }

    @Test
    fun reversingTurnsTheQueueAround() {
        assertEquals(listOf(3, 2, 1, 0), QueueOrder.reversed(4))
        assertEquals(emptyList<Int>(), QueueOrder.reversed(0))
    }

    @Test
    fun theFilterFindsSongsByTitleArtistOrAlbum() {
        val queue = listOf(a, b, c)
        assertEquals(listOf(1), QueueOrder.filter(queue, "blue"))
        assertEquals(listOf(0, 2), QueueOrder.filter(queue, "SLOW"))
        assertEquals(listOf(2), QueueOrder.filter(queue, "pygma"))
    }

    @Test
    fun anEmptyOrBlankQueryShowsEverything() {
        assertEquals(listOf(0, 1, 2), QueueOrder.filter(listOf(a, b, c), ""))
        assertEquals(listOf(0, 1, 2), QueueOrder.filter(listOf(a, b, c), "  "))
    }

    @Test
    fun theFilterKeepsEveryPlaceOfASongThatIsThereSeveralTimes() {
        assertEquals(listOf(0, 2), QueueOrder.filter(listOf(a, b, a), "alison"))
    }

    @Test
    fun theFilterIgnoresSpacesAroundTheQuery() {
        assertEquals(listOf(1), QueueOrder.filter(listOf(a, b, c), "  blue "))
    }

    @Test
    fun nothingMatchingGivesNothing() {
        assertEquals(emptyList<Int>(), QueueOrder.filter(listOf(a, b, c), "zzz"))
    }
}
