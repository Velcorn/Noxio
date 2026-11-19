/*
 * Copyright (c) 2026 Auxio Project
 * QueueMenuTest.kt is part of Auxio.
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

import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.musikr.Song

/** "Remove from queue" in the menu of a long press removes the place the menu was opened for. */
class QueueMenuTest {
    private val manager = mockk<PlaybackStateManager>(relaxed = true)
    private val a = mockk<Song>()
    private val b = mockk<Song>()

    private fun model(vararg queue: Song) =
        QueueViewModel(manager).also { it.onNewPlayback(null, queue.toList(), 0, false) }

    @Test
    fun theSecondPlaceOfASongTwiceInTheQueueIsRemoved() {
        val model = model(a, b, a)
        model.openMenu(2)
        model.removeMenuSong(a)
        verify(exactly = 1) { manager.removeQueueItem(2) }
    }

    @Test
    fun aQueueThatChangedRemovesTheFirstPlaceOfTheSong() {
        val model = model(a, b)
        model.openMenu(0)
        // The queue moved on while the menu was open: b is now first.
        model.onNewPlayback(null, listOf(b, a), 0, false)
        model.removeMenuSong(a)
        verify(exactly = 1) { manager.removeQueueItem(1) }
    }

    @Test
    fun aSongThatLeftTheQueueRemovesNothing() {
        val model = model(a, b)
        model.openMenu(0)
        model.onNewPlayback(null, listOf(b), 0, false)
        model.removeMenuSong(a)
        verify(exactly = 0) { manager.removeQueueItem(any()) }
    }
}
