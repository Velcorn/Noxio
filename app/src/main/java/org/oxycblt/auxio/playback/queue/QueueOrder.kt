/*
 * Copyright (c) 2026 Auxio Project
 * QueueOrder.kt is part of Auxio.
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

import org.oxycblt.musikr.Song
import org.oxycblt.musikr.tag.Name

/**
 * The orders and the filter of the queue. An order is a list of positions of the queue as it is
 * shown: the song at position `order[0]` becomes the first, and so on. The same song may be in the
 * queue several times, so positions are used and not the songs.
 *
 * @author Noxio
 */
object QueueOrder {
    /**
     * The order that gives [sorted], a sorted copy of [queue], by position.
     *
     * @param queue The queue as it is.
     * @param sorted The same songs in the order wanted.
     */
    fun matching(queue: List<Song>, sorted: List<Song>): List<Int> {
        require(queue.size == sorted.size) { "Sorted queue does not have the songs of the queue" }
        // A song that is in the queue several times is interchangeable with itself, so each one
        // takes the next free position of that song.
        val free = HashMap<Song, ArrayDeque<Int>>()
        queue.forEachIndexed { index, song -> free.getOrPut(song) { ArrayDeque() }.add(index) }
        return sorted.map {
            requireNotNull(free[it]?.removeFirstOrNull()) { "Sorted queue has a song too many" }
        }
    }

    /**
     * The positions of the songs of [queue] that match [query] in the title, the artist or the
     * album. The raw names are used so that no Context is needed. Everything matches an empty
     * query.
     */
    fun filter(queue: List<Song>, query: String): List<Int> {
        val needle = query.trim()
        if (needle.isEmpty()) {
            return queue.indices.toList()
        }
        fun Name.matches() = (this as? Name.Known)?.raw?.contains(needle, ignoreCase = true) == true
        return queue.indices.filter { index ->
            val song = queue[index]
            song.name.matches() ||
                song.artists.any { it.name.matches() } ||
                song.album.name.matches()
        }
    }
}
