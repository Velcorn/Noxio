/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistSongs.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music

/**
 * How the songs of a playlist change when songs are added to it or removed from it. A song is in a
 * playlist once, unless it was put there twice on purpose by an older version.
 */
object PlaylistSongs {
    /** The items of [adding] that are not in [existing] yet, each of them only once. */
    fun <T> newOnes(existing: List<T>, adding: List<T>): List<T> {
        val seen = existing.toHashSet()
        return adding.filter { seen.add(it) }
    }

    /** [songs] with every item only at its first place. */
    fun <T> withoutDuplicates(songs: List<T>): List<T> = songs.distinct()

    /** Whether [songs] has an item more than once. */
    fun <T> hasDuplicates(songs: List<T>) = songs.size != songs.toHashSet().size

    /** [songs] without its first [song], so a copy that is left stays where it was. */
    fun <T> withoutOne(songs: List<T>, song: T): List<T> {
        val index = songs.indexOf(song)
        return if (index < 0) songs else songs.toMutableList().also { it.removeAt(index) }
    }
}
