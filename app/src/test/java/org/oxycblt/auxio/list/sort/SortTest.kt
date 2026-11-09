/*
 * Copyright (c) 2026 Auxio Project
 * SortTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.list.sort

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.oxycblt.auxio.list.sort.Sort.Direction.ASCENDING
import org.oxycblt.auxio.list.sort.Sort.Direction.DESCENDING
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.tag.Date
import org.oxycblt.musikr.tag.Disc
import org.oxycblt.musikr.tag.interpret.Naming

/**
 * The orders the lists can be in. Items are mocks that only know what a sort looks at, and the
 * mocks give everything else the same value, so only the property under test can decide.
 */
class SortTest {
    private val naming = Naming.simple()

    private fun name(raw: String) = naming.name(raw, null)

    private fun range(year: Int) = Date.Range(Date.from(year)!!, Date.from(year)!!)

    private fun song(
        name: String,
        track: Int? = null,
        disc: Int? = null,
        durationMs: Long = 0,
        addedMs: Long = 0,
        album: Album = mockk(relaxed = true),
        artist: String? = null,
    ): Song =
        mockk(relaxed = true) {
            every { this@mockk.name } returns name(name)
            every { this@mockk.track } returns track
            every { this@mockk.disc } returns disc?.let { Disc(it, null) }
            every { this@mockk.durationMs } returns durationMs
            every { this@mockk.addedMs } returns addedMs
            every { this@mockk.album } returns album
            every { artists } returns
                listOfNotNull(
                    artist?.let { n ->
                        mockk<Artist>(relaxed = true) { every { this@mockk.name } returns name(n) }
                    }
                )
        }

    private fun album(
        name: String,
        year: Int? = null,
        durationMs: Long = 0,
        addedMs: Long = 0,
        songs: Int = 0,
        artist: String? = null,
    ): Album =
        mockk(relaxed = true) {
            every { this@mockk.name } returns name(name)
            every { dates } returns year?.let(::range)
            every { this@mockk.durationMs } returns durationMs
            every { this@mockk.addedMs } returns addedMs
            every { this@mockk.songs } returns List(songs) { mockk<Song>(relaxed = true) }
            every { artists } returns
                listOfNotNull(
                    artist?.let { n ->
                        mockk<Artist>(relaxed = true) { every { this@mockk.name } returns name(n) }
                    }
                )
        }

    private fun artist(name: String, durationMs: Long = 0, songs: Int = 0): Artist =
        mockk(relaxed = true) {
            every { this@mockk.name } returns name(name)
            every { this@mockk.durationMs } returns durationMs
            every { this@mockk.songs } returns List(songs) { mockk<Song>(relaxed = true) }
        }

    private fun genre(name: String, durationMs: Long = 0, songs: Int = 0): Genre =
        mockk(relaxed = true) {
            every { this@mockk.name } returns name(name)
            every { this@mockk.durationMs } returns durationMs
            every { this@mockk.songs } returns List(songs) { mockk<Song>(relaxed = true) }
        }

    private fun playlist(name: String, durationMs: Long = 0, songs: Int = 0): Playlist =
        mockk(relaxed = true) {
            every { this@mockk.name } returns name(name)
            every { this@mockk.durationMs } returns durationMs
            every { this@mockk.songs } returns List(songs) { mockk<Song>(relaxed = true) }
        }

    private fun Collection<Song>.names() = map {
        (it.name as org.oxycblt.musikr.tag.Name.Known).raw
    }

    private fun Collection<Album>.albumNames() = map {
        (it.name as org.oxycblt.musikr.tag.Name.Known).raw
    }

    private fun Collection<Artist>.artistNames() = map {
        (it.name as org.oxycblt.musikr.tag.Name.Known).raw
    }

    private fun Collection<Genre>.genreNames() = map {
        (it.name as org.oxycblt.musikr.tag.Name.Known).raw
    }

    private fun Collection<Playlist>.playlistNames() = map {
        (it.name as org.oxycblt.musikr.tag.Name.Known).raw
    }

    // --- Codes ---

    private val modes =
        listOf(
            Sort.Mode.ByName,
            Sort.Mode.ByArtist,
            Sort.Mode.ByAlbum,
            Sort.Mode.ByDate,
            Sort.Mode.ByDuration,
            Sort.Mode.ByCount,
            Sort.Mode.ByDisc,
            Sort.Mode.ByTrack,
            Sort.Mode.ByDateAdded,
            Sort.Mode.ByCustom,
        )

    @Test
    fun everySortSurvivesBeingStoredAsAnInteger() {
        for (mode in modes) {
            for (direction in listOf(ASCENDING, DESCENDING)) {
                val sort = Sort(mode, direction)
                assertEquals(sort, Sort.fromIntCode(sort.intCode))
            }
        }
    }

    @Test
    fun everyModeHasItsOwnCodeAndLabel() {
        assertEquals(modes.size, modes.map { it.intCode }.toSet().size)
        assertEquals(modes.size, modes.map { it.stringRes }.toSet().size)
        modes.forEach { assertEquals(it, Sort.Mode.fromIntCode(it.intCode)) }
    }

    @Test
    fun anUnknownCodeIsNoSort() {
        assertNull(Sort.Mode.fromIntCode(-1))
        assertNull(Sort.fromIntCode(0x7FFF_FFF0))
    }

    @Test
    fun theLowestBitOfACodeIsTheDirection() {
        assertEquals(
            ASCENDING,
            Sort.fromIntCode(Sort(Sort.Mode.ByName, ASCENDING).intCode)!!.direction,
        )
        assertEquals(
            DESCENDING,
            Sort.fromIntCode(Sort(Sort.Mode.ByName, DESCENDING).intCode)!!.direction,
        )
    }

    // --- Songs ---

    private val songs = listOf(song("b"), song("c"), song("a"))

    @Test
    fun songsByNameBothWays() {
        assertEquals(listOf("a", "b", "c"), Sort(Sort.Mode.ByName, ASCENDING).songs(songs).names())
        assertEquals(listOf("c", "b", "a"), Sort(Sort.Mode.ByName, DESCENDING).songs(songs).names())
    }

    @Test
    fun songsByDurationAndTheNameBreaksATie() {
        val list =
            listOf(song("x", durationMs = 5), song("b", durationMs = 9), song("a", durationMs = 5))
        assertEquals(
            listOf("a", "x", "b"),
            Sort(Sort.Mode.ByDuration, ASCENDING).songs(list).names(),
        )
        assertEquals(
            listOf("b", "a", "x"),
            Sort(Sort.Mode.ByDuration, DESCENDING).songs(list).names(),
        )
    }

    @Test
    fun songsByTrackWithinADisc() {
        val list =
            listOf(
                song("d2t1", track = 1, disc = 2),
                song("d1t2", track = 2, disc = 1),
                song("d1t1", track = 1, disc = 1),
            )
        assertEquals(
            listOf("d1t1", "d1t2", "d2t1"),
            Sort(Sort.Mode.ByTrack, ASCENDING).songs(list).names(),
        )
        assertEquals(
            listOf("d1t2", "d1t1", "d2t1"),
            Sort(Sort.Mode.ByTrack, DESCENDING).songs(list).names(),
        )
    }

    @Test
    fun songsByDiscThenTrack() {
        val list = listOf(song("d2t1", 1, 2), song("d1t2", 2, 1), song("d1t1", 1, 1))
        assertEquals(
            listOf("d1t1", "d1t2", "d2t1"),
            Sort(Sort.Mode.ByDisc, ASCENDING).songs(list).names(),
        )
        assertEquals(
            listOf("d2t1", "d1t1", "d1t2"),
            Sort(Sort.Mode.ByDisc, DESCENDING).songs(list).names(),
        )
    }

    @Test
    fun songsByDateAdded() {
        val list = listOf(song("a", addedMs = 30), song("b", addedMs = 10), song("c", addedMs = 20))
        assertEquals(
            listOf("b", "c", "a"),
            Sort(Sort.Mode.ByDateAdded, ASCENDING).songs(list).names(),
        )
        assertEquals(
            listOf("a", "c", "b"),
            Sort(Sort.Mode.ByDateAdded, DESCENDING).songs(list).names(),
        )
    }

    @Test
    fun songsByAlbumKeepTheirOrderInsideTheAlbum() {
        val first = album("First")
        val second = album("Second")
        val list =
            listOf(
                song("s2t1", track = 1, album = second),
                song("f1t2", track = 2, album = first),
                song("f1t1", track = 1, album = first),
            )
        assertEquals(
            listOf("f1t1", "f1t2", "s2t1"),
            Sort(Sort.Mode.ByAlbum, ASCENDING).songs(list).names(),
        )
        assertEquals(
            listOf("s2t1", "f1t1", "f1t2"),
            Sort(Sort.Mode.ByAlbum, DESCENDING).songs(list).names(),
        )
    }

    @Test
    fun songsByDateGoByTheirAlbum() {
        val old = album("Old", year = 1999)
        val new = album("New", year = 2020)
        val list = listOf(song("n", album = new), song("o", album = old))
        assertEquals(listOf("o", "n"), Sort(Sort.Mode.ByDate, ASCENDING).songs(list).names())
        assertEquals(listOf("n", "o"), Sort(Sort.Mode.ByDate, DESCENDING).songs(list).names())
    }

    @Test
    fun songsByArtistAndTheNewestAlbumOfAnArtistComesFirst() {
        val oldAlbum = album("Old", year = 1999)
        val newAlbum = album("New", year = 2020)
        val list =
            listOf(
                song("z-old", album = oldAlbum, artist = "Z"),
                song("a-song", album = newAlbum, artist = "A"),
                song("z-new", album = newAlbum, artist = "Z"),
            )
        assertEquals(
            listOf("a-song", "z-new", "z-old"),
            Sort(Sort.Mode.ByArtist, ASCENDING).songs(list).names(),
        )
        assertEquals(
            listOf("z-new", "z-old", "a-song"),
            Sort(Sort.Mode.ByArtist, DESCENDING).songs(list).names(),
        )
    }

    @Test
    fun aCustomOrderKeepsTheListAsItIs() {
        assertEquals(
            listOf("b", "c", "a"),
            Sort(Sort.Mode.ByCustom, ASCENDING).songs(songs).names(),
        )
        assertEquals(
            listOf("b", "c", "a"),
            Sort(Sort.Mode.ByCustom, DESCENDING).songs(songs).names(),
        )
    }

    @Test
    fun theOriginalListIsNotChanged() {
        val original = songs.toList()
        Sort(Sort.Mode.ByName, ASCENDING).songs(songs)
        assertEquals(original, songs)
    }

    @Test
    fun aModeThatDoesNotApplyToAKindOfListSaysSoInsteadOfGuessing() {
        assertThrows(NotImplementedError::class.java) {
            Sort(Sort.Mode.ByCount, ASCENDING).songs(songs)
        }
    }

    // --- Albums ---

    @Test
    fun albumsByNameDateDurationCountAndDateAdded() {
        val list =
            listOf(
                album("b", year = 2001, durationMs = 30, songs = 3, addedMs = 5),
                album("c", year = 1990, durationMs = 10, songs = 1, addedMs = 9),
                album("a", year = 2010, durationMs = 20, songs = 2, addedMs = 1),
            )
        assertEquals(
            listOf("a", "b", "c"),
            Sort(Sort.Mode.ByName, ASCENDING).albums(list).albumNames(),
        )
        assertEquals(
            listOf("c", "b", "a"),
            Sort(Sort.Mode.ByDate, ASCENDING).albums(list).albumNames(),
        )
        assertEquals(
            listOf("a", "b", "c"),
            Sort(Sort.Mode.ByDate, DESCENDING).albums(list).albumNames(),
        )
        assertEquals(
            listOf("c", "a", "b"),
            Sort(Sort.Mode.ByDuration, ASCENDING).albums(list).albumNames(),
        )
        assertEquals(
            listOf("b", "a", "c"),
            Sort(Sort.Mode.ByDuration, DESCENDING).albums(list).albumNames(),
        )
        assertEquals(
            listOf("c", "a", "b"),
            Sort(Sort.Mode.ByCount, ASCENDING).albums(list).albumNames(),
        )
        assertEquals(
            listOf("b", "a", "c"),
            Sort(Sort.Mode.ByCount, DESCENDING).albums(list).albumNames(),
        )
        assertEquals(
            listOf("a", "b", "c"),
            Sort(Sort.Mode.ByDateAdded, ASCENDING).albums(list).albumNames(),
        )
        assertEquals(
            listOf("c", "b", "a"),
            Sort(Sort.Mode.ByDateAdded, DESCENDING).albums(list).albumNames(),
        )
    }

    @Test
    fun albumsByArtistShowTheNewestOfAnArtistFirst() {
        val list =
            listOf(
                album("old", year = 1999, artist = "Z"),
                album("a-album", year = 2005, artist = "A"),
                album("new", year = 2020, artist = "Z"),
            )
        assertEquals(
            listOf("a-album", "new", "old"),
            Sort(Sort.Mode.ByArtist, ASCENDING).albums(list).albumNames(),
        )
        assertEquals(
            listOf("new", "old", "a-album"),
            Sort(Sort.Mode.ByArtist, DESCENDING).albums(list).albumNames(),
        )
    }

    // --- Artists, genres, playlists ---

    @Test
    fun artistsByNameDurationAndCount() {
        val list = listOf(artist("b", 20, 2), artist("c", 30, 1), artist("a", 10, 3))
        assertEquals(
            listOf("a", "b", "c"),
            Sort(Sort.Mode.ByName, ASCENDING).artists(list).artistNames(),
        )
        assertEquals(
            listOf("c", "b", "a"),
            Sort(Sort.Mode.ByName, DESCENDING).artists(list).artistNames(),
        )
        assertEquals(
            listOf("a", "b", "c"),
            Sort(Sort.Mode.ByDuration, ASCENDING).artists(list).artistNames(),
        )
        assertEquals(
            listOf("c", "b", "a"),
            Sort(Sort.Mode.ByDuration, DESCENDING).artists(list).artistNames(),
        )
        assertEquals(
            listOf("c", "b", "a"),
            Sort(Sort.Mode.ByCount, ASCENDING).artists(list).artistNames(),
        )
        assertEquals(
            listOf("a", "b", "c"),
            Sort(Sort.Mode.ByCount, DESCENDING).artists(list).artistNames(),
        )
    }

    @Test
    fun genresByNameDurationAndCount() {
        val list = listOf(genre("b", 20, 2), genre("c", 30, 1), genre("a", 10, 3))
        assertEquals(
            listOf("a", "b", "c"),
            Sort(Sort.Mode.ByName, ASCENDING).genres(list).genreNames(),
        )
        assertEquals(
            listOf("c", "b", "a"),
            Sort(Sort.Mode.ByName, DESCENDING).genres(list).genreNames(),
        )
        assertEquals(
            listOf("a", "b", "c"),
            Sort(Sort.Mode.ByDuration, ASCENDING).genres(list).genreNames(),
        )
        assertEquals(
            listOf("c", "b", "a"),
            Sort(Sort.Mode.ByDuration, DESCENDING).genres(list).genreNames(),
        )
        assertEquals(
            listOf("c", "b", "a"),
            Sort(Sort.Mode.ByCount, ASCENDING).genres(list).genreNames(),
        )
        assertEquals(
            listOf("a", "b", "c"),
            Sort(Sort.Mode.ByCount, DESCENDING).genres(list).genreNames(),
        )
    }

    @Test
    fun playlistsByNameDurationAndCount() {
        val list = listOf(playlist("b", 20, 2), playlist("c", 30, 1), playlist("a", 10, 3))
        assertEquals(
            listOf("a", "b", "c"),
            Sort(Sort.Mode.ByName, ASCENDING).playlists(list).playlistNames(),
        )
        assertEquals(
            listOf("c", "b", "a"),
            Sort(Sort.Mode.ByName, DESCENDING).playlists(list).playlistNames(),
        )
        assertEquals(
            listOf("a", "b", "c"),
            Sort(Sort.Mode.ByDuration, ASCENDING).playlists(list).playlistNames(),
        )
        assertEquals(
            listOf("c", "b", "a"),
            Sort(Sort.Mode.ByDuration, DESCENDING).playlists(list).playlistNames(),
        )
        assertEquals(
            listOf("c", "b", "a"),
            Sort(Sort.Mode.ByCount, ASCENDING).playlists(list).playlistNames(),
        )
        assertEquals(
            listOf("a", "b", "c"),
            Sort(Sort.Mode.ByCount, DESCENDING).playlists(list).playlistNames(),
        )
    }

    @Test
    fun trackAndDiscOnlyApplyToSongs() {
        for (mode in listOf(Sort.Mode.ByTrack, Sort.Mode.ByDisc)) {
            assertThrows(NotImplementedError::class.java) {
                Sort(mode, ASCENDING).artists(listOf(artist("a")))
            }
            assertThrows(NotImplementedError::class.java) {
                Sort(mode, ASCENDING).genres(listOf(genre("a")))
            }
        }
    }
}
