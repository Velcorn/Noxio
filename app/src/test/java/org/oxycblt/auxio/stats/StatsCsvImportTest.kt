/*
 * Copyright (c) 2026 Auxio Project
 * StatsCsvImportTest.kt is part of Auxio.
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCsvImportTest {
    private val header =
        "FILE_PATH,TITLE,ARTIST,ALBUM,ALBUM_ARTIST,COMPOSER,GENRE,YEAR,DURATION_MS,PLAY_COUNT"

    private fun mostPlayed(text: String) =
        (StatsCsvImport.parse(text) as StatsCsvImport.Parsed.MostPlayed).rows

    private fun history(text: String) =
        (StatsCsvImport.parse(text) as StatsCsvImport.Parsed.History).events

    // --- readRows ---

    @Test
    fun readRowsSplitsSimpleFields() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("c", "d")),
            StatsCsvImport.readRows("a,b\nc,d\n"),
        )
    }

    @Test
    fun readRowsKeepsCommasQuotesAndLineBreaksInsideQuotes() {
        val rows = StatsCsvImport.readRows("\"a,b\",\"say \"\"hi\"\"\",\"x\ny\"\n")
        assertEquals(listOf(listOf("a,b", "say \"hi\"", "x\ny")), rows)
    }

    @Test
    fun readRowsHandlesWindowsLineEndingsAndAMissingFinalNewline() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("c", "d")),
            StatsCsvImport.readRows("a,b\r\nc,d"),
        )
    }

    @Test
    fun readRowsIgnoresAByteOrderMarkAndBlankLines() {
        assertEquals(listOf(listOf("a"), listOf("b")), StatsCsvImport.readRows("﻿a\n\n,\nb\n"))
    }

    @Test
    fun readRowsKeepsEmptyCellsInTheMiddle() {
        assertEquals(listOf(listOf("a", "", "c")), StatsCsvImport.readRows("a,,c\n"))
    }

    // --- parse ---

    @Test
    fun parseRejectsFilesThatAreNotStats() {
        assertNull(StatsCsvImport.parse(""))
        assertNull(StatsCsvImport.parse("name,value\nx,1\n"))
        assertNull(StatsCsvImport.parse("TITLE,ARTIST\nx,y\n")) // no PLAY_COUNT
    }

    @Test
    fun parseReadsAMusicoletMostPlayedList() {
        val rows =
            mostPlayed(
                "$header\n" +
                    "Music/a.mp3,Song A,Artist,Album,<unknown>,<unknown>,Rock,2019,180000,42\n" +
                    "Music/b.mp3,\"Song, B\",Artist 2,<unknown>,<unknown>,<unknown>,<unknown>,0,1,7\n"
            )
        assertEquals(2, rows.size)
        assertEquals("Music/a.mp3", rows[0].filePath)
        assertEquals("Song A", rows[0].title)
        assertEquals("Album", rows[0].album)
        assertEquals("", rows[0].albumArtist) // <unknown> is empty
        assertEquals(2019, rows[0].year)
        assertEquals(180000L, rows[0].durationMs)
        assertEquals(42, rows[0].plays)
        assertEquals("Song, B", rows[1].title)
        assertEquals("", rows[1].album)
    }

    @Test
    fun parseDropsRowsWithoutAUsableCountOrTitle() {
        val rows =
            mostPlayed(
                "$header\n" +
                    "a,Zero,x,x,x,x,x,0,0,0\n" +
                    "a,Negative,x,x,x,x,x,0,0,-3\n" +
                    "a,Text,x,x,x,x,x,0,0,many\n" +
                    "a,,x,x,x,x,x,0,0,5\n" +
                    "a,Good,x,x,x,x,x,0,0,5\n"
            )
        assertEquals(listOf("Good"), rows.map { it.title })
    }

    @Test
    fun parseCapsAbsurdPlayCounts() {
        val rows = mostPlayed("$header\na,Song,x,x,x,x,x,0,0,999999\n")
        assertEquals(10_000, rows.single().plays)
    }

    @Test
    fun parseToleratesColumnsInAnyOrderAndLowerCaseNames() {
        val rows = mostPlayed("play_count,title\n3,Song\n")
        assertEquals("Song", rows.single().title)
        assertEquals(3, rows.single().plays)
    }

    @Test
    fun historyRoundTripsThroughTheWriter() {
        val sep = PlayEvent.ARTIST_SEPARATOR
        val original =
            PlayEvent(
                songUid = "x",
                title = "Hello, \"World\"",
                artists = "A${sep}B",
                album = "Alb",
                startedAt = 1_700_000_000_000L,
                listenedMs = 123_000,
                durationMs = 200_000,
                filePath = "Music/x.mp3",
                albumArtist = "A",
                genre = "Pop",
                year = 2001,
            )
        val out = ByteArrayOutputStream()
        StatsCsv.writeHistory(listOf(original), out, ZoneOffset.UTC)

        val read = history(out.toString(Charsets.UTF_8)).single()
        assertEquals(original.title, read.title)
        assertEquals(original.artists, read.artists)
        assertEquals(original.album, read.album)
        assertEquals(original.startedAt, read.startedAt)
        assertEquals(original.listenedMs, read.listenedMs)
        assertEquals(original.durationMs, read.durationMs)
        assertEquals(original.filePath, read.filePath)
        assertEquals(original.albumArtist, read.albumArtist)
        assertEquals(original.genre, read.genre)
        assertEquals(original.year, read.year)
    }

    @Test
    fun anEmptyTimeInAHistoryIsAPlayFromBeforeNoxio() {
        val events = history("$header,PLAYED_AT,LISTENED_MS\na,Old,x,x,x,x,x,0,60000,1,,50000\n")
        assertEquals(PlayEvent.BASELINE_TIME, events.single().startedAt)
        assertEquals(50000L, events.single().listenedMs)
    }

    @Test
    fun aHistoryRowWithAnInvalidTimeIsSkipped() {
        val events =
            history(
                "$header,PLAYED_AT,LISTENED_MS\n" +
                    "a,Bad,x,x,x,x,x,0,1,1,yesterday,1\n" +
                    "a,Good,x,x,x,x,x,0,1,1,2023-11-14T22:13:20Z,1\n"
            )
        assertEquals(listOf("Good"), events.map { it.title })
    }

    @Test
    fun listenedTimeDefaultsToTheDurationWhenMissing() {
        val events = history("$header,PLAYED_AT\na,Song,x,x,x,x,x,0,90000,1,2023-11-14T22:13:20Z\n")
        assertEquals(90000L, events.single().listenedMs)
    }

    // --- baseline and keys ---

    @Test
    fun baselineEventsRepeatEachSongByItsPlayCountWithoutADate() {
        val rows =
            listOf(
                StatsCsvImport.MostPlayedRow(
                    "a.mp3",
                    "A",
                    "X; Y",
                    "Alb",
                    "X",
                    "Rock",
                    2000,
                    1000,
                    3,
                ),
                StatsCsvImport.MostPlayedRow("", "B", "Z", "", "", "", 0, 0, 1),
            )
        val events = StatsCsvImport.baselineEvents(rows)

        assertEquals(4, events.size)
        assertTrue(events.all { it.startedAt == PlayEvent.BASELINE_TIME })
        assertEquals(3, events.count { it.title == "A" })
        assertEquals("X${PlayEvent.ARTIST_SEPARATOR}Y", events.first().artists)
        assertEquals("import:a.mp3", events.first().songUid)
        // Without a file the title and artist identify the song.
        assertEquals("import:BZ", events.last().songUid)
        assertEquals(1000L, events.first().listenedMs)
    }

    @Test
    fun twoPlaysInTheSameSecondOfTheSameSongAreTheSamePlay() {
        val base =
            PlayEvent(
                songUid = "1",
                title = "T",
                artists = "",
                album = "A",
                startedAt = 5_000,
                listenedMs = 1,
                durationMs = 1,
            )
        assertEquals(
            StatsCsvImport.historyKey(base),
            StatsCsvImport.historyKey(base.copy(startedAt = 5_999, songUid = "other")),
        )
        assertNotNull(StatsCsvImport.historyKey(base))
        assertTrue(
            StatsCsvImport.historyKey(base) !=
                StatsCsvImport.historyKey(base.copy(startedAt = 6_000))
        )
    }
}
