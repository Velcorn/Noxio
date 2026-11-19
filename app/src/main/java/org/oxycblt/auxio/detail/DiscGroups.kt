/*
 * Copyright (c) 2026 Auxio Project
 * DiscGroups.kt is part of Auxio.
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

/**
 * Noxio: the songs of an album with several discs, grouped by disc. The discs always come in their
 * own order (songs without a disc first), reversed for a descending sort, whatever the songs are
 * sorted by; within a disc the songs keep the order they were given in.
 */
object DiscGroups {
    fun <T, D : Comparable<D>> group(
        songs: List<T>,
        descending: Boolean,
        disc: (T) -> D?,
    ): Map<D?, List<T>> {
        val order = nullsFirst<D>(naturalOrder())
        return songs.groupBy(disc).toSortedMap(if (descending) order.reversed() else order)
    }
}
