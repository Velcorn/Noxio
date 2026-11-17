/*
 * Copyright (c) 2026 Auxio Project
 * QueueSwapTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.state

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueSwapTest {
    private val uids =
        List(3) { Music.UID.fromString("uas00000000-0000-0000-0000-00000000000$it")!! }

    /** A new object for song [n], as a library that was loaded again has. */
    private fun song(n: Int, file: String = "content://media/$n") =
        mockk<Song> {
            every { uid } returns uids[n]
            every { uri } returns Uri.parse(file)
        }

    private fun queue(vararg songs: Song, mapping: List<Int> = emptyList(), index: Int = 0) =
        RawQueue(songs.toList(), mapping, index)

    @Test
    fun theSameSongsAsNewObjectsAreTheSameQueue() {
        assertTrue(
            QueueSwap.isSameQueue(
                queue(song(0), song(1), song(2), mapping = listOf(2, 0, 1), index = 2),
                queue(song(0), song(1), song(2), mapping = listOf(2, 0, 1), index = 2),
            ),
        )
    }

    @Test
    fun anotherOrderOrSongIsNot() {
        val current = queue(song(0), song(1))
        assertFalse(QueueSwap.isSameQueue(current, queue(song(1), song(0))))
        assertFalse(QueueSwap.isSameQueue(current, queue(song(0), song(2))))
        assertFalse(QueueSwap.isSameQueue(current, queue(song(0))))
    }

    @Test
    fun anotherShuffleOrPlayingSongIsNot() {
        val current = queue(song(0), song(1), mapping = listOf(0, 1))
        assertFalse(QueueSwap.isSameQueue(current, queue(song(0), song(1), mapping = listOf(1, 0))))
        assertFalse(QueueSwap.isSameQueue(current, queue(song(0), song(1))))
        assertFalse(
            QueueSwap.isSameQueue(
                current,
                queue(song(0), song(1), mapping = listOf(0, 1), index = 1),
            ),
        )
    }

    @Test
    fun aSongWithEditedTagsIsStillTheSameQueue() {
        // Its UID changed with its tags, the file is the same.
        val edited =
            mockk<Song> {
                every { uid } returns uids[2]
                every { uri } returns Uri.parse("content://media/0")
            }
        assertTrue(QueueSwap.isSameQueue(queue(song(0), song(1)), queue(edited, song(1))))
    }

    @Test
    fun aSongWhoseFileMovedIsNot() {
        // The player would have to open the new file, so it starts over.
        assertFalse(
            QueueSwap.isSameQueue(queue(song(0)), queue(song(0, file = "content://media/moved"))),
        )
    }

    @Test
    fun anEmptyQueueIsNot() {
        assertFalse(QueueSwap.isSameQueue(queue(), queue()))
    }

    @Test
    fun theWholeQueueAroundTheOnlySongIsAGrowth() {
        assertTrue(
            QueueSwap.isGrowth(
                queue(song(1)),
                queue(song(0), song(1), song(2), mapping = listOf(2, 1, 0), index = 1),
            ),
        )
    }

    @Test
    fun aQueueAroundAnotherSongOrFileIsNoGrowth() {
        val whole = queue(song(0), song(1), song(2), index = 1)
        assertFalse(QueueSwap.isGrowth(queue(song(0)), whole))
        assertFalse(QueueSwap.isGrowth(queue(song(1, file = "content://media/moved")), whole))
        // Not from more than one song, and not to only one.
        assertFalse(QueueSwap.isGrowth(queue(song(0), song(1)), whole))
        assertFalse(QueueSwap.isGrowth(queue(song(1)), queue(song(1))))
        assertFalse(QueueSwap.isGrowth(queue(), whole))
    }
}
