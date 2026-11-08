/*
 * Copyright (c) 2026 Auxio Project
 * StatsCsvTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.stats

import java.io.ByteArrayOutputStream
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsCsvTest {
    private val sep = PlayEvent.ARTIST_SEPARATOR

    private fun play(
        title: String = "Song",
        artists: String = "Artist",
        album: String = "Album",
        startedAt: Long = 1_700_000_000_000L, // 2023-11-14T22:13:20Z
        listenedMs: Long = 200_000,
        durationMs: Long = 210_000,
        filePath: String = "Music/song.mp3",
        albumArtist: String = "",
        genre: String = "Rock",
        year: Int = 2020,
    ) =
        PlayEvent(
            songUid = "uid-$title",
            title = title,
            artists = artists,
            album = album,
            startedAt = startedAt,
            listenedMs = listenedMs,
            durationMs = durationMs,
            filePath = filePath,
            albumArtist = albumArtist,
            genre = genre,
            year = year,
        )

    private fun history(vararg events: PlayEvent): List<String> {
        val out = ByteArrayOutputStream()
        StatsCsv.writeHistory(events.toList(), out, ZoneOffset.UTC)
        return out.toString(Charsets.UTF_8).lines().filter { it.isNotEmpty() }
    }

    private fun mostPlayed(vararg events: PlayEvent): List<String> {
        val out = ByteArrayOutputStream()
        StatsCsv.writeMostPlayed(events.toList(), out)
        return out.toString(Charsets.UTF_8).lines().filter { it.isNotEmpty() }
    }

    @Test
    fun historyStartsWithTheMusicoletColumnsAndTwoExtras() {
        assertEquals(
            "FILE_PATH,TITLE,ARTIST,ALBUM,ALBUM_ARTIST,COMPOSER,GENRE,YEAR,DURATION_MS," +
                "PLAY_COUNT,PLAYED_AT,LISTENED_MS",
            history().single(),
        )
    }

    @Test
    fun historyRowHasOnePlayAnIsoTimeAndPlaceholdersForUnknowns() {
        val rows = history(play())
        assertEquals(
            "Music/song.mp3,Song,Artist,Album,<unknown>,<unknown>,Rock,2020,210000,1," +
                "2023-11-14T22:13:20Z,200000",
            rows[1],
        )
    }

    @Test
    fun historyUsesTheGivenTimeZone() {
        val out = ByteArrayOutputStream()
        StatsCsv.writeHistory(listOf(play()), out, ZoneOffset.ofHours(2))
        val row = out.toString(Charsets.UTF_8).lines()[1]
        assertEquals(true, row.contains("2023-11-15T00:13:20+02:00"))
    }

    @Test
    fun severalArtistsAreJoinedWithSemicolons() {
        val rows = history(play(artists = "A${sep}B", albumArtist = "C${sep}D"))
        val cells = rows[1].split(",")
        assertEquals("A; B", cells[2])
        assertEquals("C; D", cells[4])
    }

    @Test
    fun emptyFieldsBecomeUnknown() {
        val cells = history(play(filePath = "", album = "", genre = "", artists = ""))[1].split(",")
        assertEquals("<unknown>", cells[0])
        assertEquals("<unknown>", cells[2])
        assertEquals("<unknown>", cells[3])
        assertEquals("<unknown>", cells[6])
    }

    @Test
    fun fieldsWithCommasQuotesAndNewlinesAreQuoted() {
        assertEquals("plain", StatsCsv.field("plain"))
        assertEquals("\"a,b\"", StatsCsv.field("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", StatsCsv.field("say \"hi\""))
        assertEquals("\"two\nlines\"", StatsCsv.field("two\nlines"))
    }

    @Test
    fun playsFromBeforeNoxioHaveAnEmptyTime() {
        val row = history(play(startedAt = PlayEvent.BASELINE_TIME))[1]
        assertEquals(true, row.endsWith(",1,,200000"))
    }

    @Test
    fun mostPlayedCountsPlaysPerSongAndSortsByCountThenTitle() {
        val rows =
            mostPlayed(
                play(title = "Beta"),
                play(title = "Alpha"),
                play(title = "Beta", startedAt = 2),
                play(title = "Gamma"),
                play(title = "Beta", startedAt = 3),
            )
        assertEquals(4, rows.size) // header and three songs
        assertEquals("Beta", rows[1].split(",")[1])
        assertEquals("3", rows[1].split(",").last())
        assertEquals("Alpha", rows[2].split(",")[1])
        assertEquals("Gamma", rows[3].split(",")[1])
    }

    @Test
    fun theSameTitleOnADifferentAlbumIsAnotherSong() {
        val rows = mostPlayed(play(album = "One"), play(album = "Two"))
        assertEquals(3, rows.size)
    }
}
