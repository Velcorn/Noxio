/*
 * Copyright (c) 2026 Auxio Project
 * QueueSwap.kt is part of Auxio.
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

/** Noxio: when a queue can take new song objects without the player starting over. */
object QueueSwap {
    /**
     * Whether [new] is [current] with other objects for the same songs: the same files in the same
     * order (the tags may have changed), the same shuffle and the same song playing. Then the
     * player's items can be replaced in place.
     */
    fun isSameQueue(current: RawQueue, new: RawQueue): Boolean =
        current.heap.isNotEmpty() &&
            current.heap.size == new.heap.size &&
            current.heapIndex == new.heapIndex &&
            current.shuffledMapping == new.shuffledMapping &&
            // By file: a song whose tags were edited is the same file with a new UID.
            current.heap.indices.all { current.heap[it].uri == new.heap[it].uri }

    /**
     * Whether [new] is the whole queue around the only song of [current] (a song restored before
     * the library had loaded): the player can add the other songs around it while it plays.
     */
    fun isGrowth(current: RawQueue, new: RawQueue): Boolean {
        val only = current.heap.singleOrNull() ?: return false
        val playing = new.heap.getOrNull(new.heapIndex) ?: return false
        return new.heap.size > 1 &&
            !current.isShuffled &&
            only.uid == playing.uid &&
            only.uri == playing.uri
    }
}
