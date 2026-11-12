/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistSongsTest.kt is part of Auxio.
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** What happens to the songs of a playlist when songs are added or taken out. */
class PlaylistSongsTest {
    @Test
    fun onlySongsThatAreNotInThePlaylistYetAreNew() {
        assertEquals(
            listOf("c", "d"),
            PlaylistSongs.newOnes(listOf("a", "b"), listOf("b", "c", "d")),
        )
    }

    @Test
    fun aSongThatIsAddedTwiceInOneGoCountsOnce() {
        assertEquals(listOf("a"), PlaylistSongs.newOnes(emptyList(), listOf("a", "a", "a")))
    }

    @Test
    fun nothingIsNewWhenEverythingIsThereAlready() {
        assertEquals(emptyList<String>(), PlaylistSongs.newOnes(listOf("a", "b"), listOf("b", "a")))
        assertEquals(emptyList<String>(), PlaylistSongs.newOnes(listOf("a"), emptyList()))
    }

    @Test
    fun duplicatesAreFoundAndDroppedKeepingTheFirstPlace() {
        val songs = listOf("a", "b", "a", "c", "b", "a")
        assertTrue(PlaylistSongs.hasDuplicates(songs))
        assertEquals(listOf("a", "b", "c"), PlaylistSongs.withoutDuplicates(songs))
        assertFalse(PlaylistSongs.hasDuplicates(listOf("a", "b")))
        assertFalse(PlaylistSongs.hasDuplicates(emptyList<String>()))
    }

    @Test
    fun removingASongTakesOutOnlyTheFirstCopy() {
        assertEquals(
            listOf("b", "a", "c"),
            PlaylistSongs.withoutOne(listOf("a", "b", "a", "c"), "a"),
        )
        assertEquals(listOf("a", "c"), PlaylistSongs.withoutOne(listOf("a", "b", "c"), "b"))
    }

    @Test
    fun removingASongThatIsNotThereChangesNothing() {
        val songs = listOf("a", "b")
        assertSame(songs, PlaylistSongs.withoutOne(songs, "z"))
    }

    @Test
    fun theListThatWasGivenIsNeverChanged() {
        val songs = listOf("a", "b", "a")
        PlaylistSongs.withoutOne(songs, "a")
        PlaylistSongs.withoutDuplicates(songs)
        assertEquals(listOf("a", "b", "a"), songs)
    }
}
