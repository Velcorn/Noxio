/*
 * Copyright (c) 2026 Auxio Project
 * StatsRepositoryTest.kt is part of Auxio.
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

import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsRepositoryTest {
    private val sep = PlayEvent.ARTIST_SEPARATOR

    private fun play(
        title: String,
        artists: String = "Artist",
        album: String = "Album",
        startedAt: Long = 1_000,
        listenedMs: Long = 100,
    ) =
        PlayEvent(
            songUid = "uid-$title",
            title = title,
            artists = artists,
            album = album,
            startedAt = startedAt,
            listenedMs = listenedMs,
            durationMs = 200,
        )

    // --- summarize ---

    @Test
    fun summarizingNothingIsEmpty() {
        val stats = summarize(emptyList())
        assertTrue(stats.isEmpty)
        assertEquals(0, stats.plays)
        assertEquals(0L, stats.listenedMs)
    }

    @Test
    fun songsAreRankedByPlaysThenByTimeListened() {
        val stats =
            summarize(
                listOf(
                    play("Once", listenedMs = 999),
                    play("Twice", listenedMs = 10),
                    play("Twice", listenedMs = 10),
                    play("Tie A", listenedMs = 500),
                    play("Tie B", listenedMs = 900),
                    play("Tie A", listenedMs = 500),
                    play("Tie B", listenedMs = 900),
                )
            )
        assertEquals(listOf("Tie B", "Tie A", "Twice", "Once"), stats.songs.map { it.name })
        assertEquals(2, stats.songs.first().plays)
        assertEquals(1800L, stats.songs.first().listenedMs)
        assertEquals(7, stats.plays)
    }

    @Test
    fun everyArtistOfAPlayGetsTheCredit() {
        val stats = summarize(listOf(play("Duet", artists = "A${sep}B")))
        assertEquals(setOf("A", "B"), stats.artists.map { it.name }.toSet())
        assertEquals("A, B", stats.songs.single().subtitle)
    }

    @Test
    fun anArtistListedTwiceOnOnePlayIsCountedOnce() {
        val stats = summarize(listOf(play("Odd", artists = "A${sep}A")))
        assertEquals(1, stats.artists.single().plays)
    }

    @Test
    fun albumsWithTheSameNameByDifferentArtistsStaySeparate() {
        val stats =
            summarize(
                listOf(
                    play("One", artists = "A", album = "Greatest Hits"),
                    play("Two", artists = "B", album = "Greatest Hits"),
                )
            )
        assertEquals(2, stats.albums.size)
    }

    @Test
    fun playsWithoutAnAlbumDoNotCountAsAnAlbum() {
        assertTrue(summarize(listOf(play("Single", album = ""))).albums.isEmpty())
    }

    @Test
    fun theSameSongIsMatchedByTextEvenWithADifferentUid() {
        val stats = summarize(listOf(play("Song"), play("Song").copy(songUid = "retagged")))
        assertEquals(1, stats.songs.size)
        assertEquals(2, stats.songs.single().plays)
    }

    @Test
    fun rankedListsAreCutAtTheLimit() {
        val events = (1..30).map { play("Song $it") }
        assertEquals(10, summarize(events).songs.size)
        assertEquals(3, summarize(events, limit = 3).songs.size)
    }

    // --- statsRange ---

    private fun utcCalendar() = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)

    private val wednesday = ZonedDateTime.of(2023, 11, 15, 12, 0, 0, 0, ZoneOffset.UTC)

    private fun at(year: Int, month: Int, day: Int) =
        ZonedDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun range(period: StatsPeriod, offset: Int) =
        statsRange(period, offset, wednesday.toInstant().toEpochMilli(), utcCalendar())

    @Test
    fun theWeekStartsOnTheFirstDayOfTheWeek() {
        // The US week starts on Sunday: Sun Nov 12 until Sun Nov 19.
        val week = range(StatsPeriod.WEEK, 0)
        assertEquals(at(2023, 11, 12), week.first)
        assertEquals(at(2023, 11, 19), week.last + 1)
    }

    @Test
    fun theMonthAndYearCoverTheWholeCalendarUnit() {
        val month = range(StatsPeriod.MONTH, 0)
        assertEquals(at(2023, 11, 1), month.first)
        assertEquals(at(2023, 12, 1), month.last + 1)
        val year = range(StatsPeriod.YEAR, 0)
        assertEquals(at(2023, 1, 1), year.first)
        assertEquals(at(2024, 1, 1), year.last + 1)
    }

    @Test
    fun negativeOffsetsGoBack() {
        assertEquals(at(2023, 11, 5), range(StatsPeriod.WEEK, -1).first)
        assertEquals(at(2023, 10, 1), range(StatsPeriod.MONTH, -1).first)
        assertEquals(at(2022, 1, 1), range(StatsPeriod.YEAR, -1).first)
    }

    @Test
    fun aMonthBackAcrossTheNewYearGoesIntoTheOldYear() {
        val january = statsRange(StatsPeriod.MONTH, -1, at(2024, 1, 10), utcCalendar())
        assertEquals(at(2023, 12, 1), january.first)
        assertEquals(at(2024, 1, 1), january.last + 1)
    }

    @Test
    fun allTimeStartsAtTheBaselineAndHasNoEnd() {
        val all = range(StatsPeriod.ALL, 0)
        assertEquals(PlayEvent.BASELINE_TIME, all.first)
        assertEquals(Long.MAX_VALUE - 1, all.last)
        assertTrue(PlayEvent.BASELINE_TIME in all)
    }

    @Test
    fun noWeekMonthOrYearContainsTheBaseline() {
        for (period in listOf(StatsPeriod.WEEK, StatsPeriod.MONTH, StatsPeriod.YEAR)) {
            for (offset in -50..0) {
                assertFalse("$period $offset", PlayEvent.BASELINE_TIME in range(period, offset))
            }
        }
    }

    // --- the repository ---

    private class FakeDao : PlayEventDao {
        val events = mutableListOf<PlayEvent>()

        override suspend fun insert(event: PlayEvent): Long {
            val saved = event.copy(id = events.size + 1L)
            events += saved
            return saved.id
        }

        override suspend fun updateListened(id: Long, listenedMs: Long) {
            val index = events.indexOfFirst { it.id == id }
            events[index] = events[index].copy(listenedMs = listenedMs)
        }

        override suspend fun insertAll(events: List<PlayEvent>) {
            this.events += events
        }

        override suspend fun deleteBaseline() {
            events.removeAll { it.startedAt == PlayEvent.BASELINE_TIME }
        }

        override suspend fun between(fromMs: Long, toMs: Long) = events.filter {
            it.startedAt in fromMs until toMs
        }

        override suspend fun all() = events.sortedBy { it.startedAt }

        override suspend fun deleteAll() = events.clear()
    }

    private val dao = FakeDao()
    private val repository = StatsRepositoryImpl(dao)

    private fun row(title: String, plays: Int) =
        StatsCsvImport.MostPlayedRow("$title.mp3", title, "Artist", "Album", "", "", 0, 1000, plays)

    @Test
    fun recordedPlaysShowUpInTheSummary() = runBlocking {
        repository.record(play("Song", startedAt = 5_000))
        assertEquals(1, repository.summary(0L until 10_000L).plays)
        assertEquals(0, repository.summary(10_000L until 20_000L).plays)
    }

    @Test
    fun importingAHistoryTwiceDoesNotDuplicateIt() = runBlocking {
        val history =
            StatsCsvImport.Parsed.History(
                listOf(play("A", startedAt = 5_000), play("B", startedAt = 9_000))
            )
        assertEquals(2, repository.importCsv(listOf(history)))
        assertEquals(0, repository.importCsv(listOf(history)))
        assertEquals(2, dao.events.size)
    }

    @Test
    fun aMostPlayedListBecomesUndatedPlays() = runBlocking {
        val added =
            repository.importCsv(
                listOf(StatsCsvImport.Parsed.MostPlayed(listOf(row("A", 3), row("B", 2))))
            )
        assertEquals(5, added)
        assertTrue(dao.events.all { it.startedAt == PlayEvent.BASELINE_TIME })
    }

    @Test
    fun theBaselineCountsInAllTimeButNotInAnyWeek() = runBlocking {
        repository.importCsv(listOf(StatsCsvImport.Parsed.MostPlayed(listOf(row("A", 4)))))
        repository.record(play("Live", startedAt = wednesday.toInstant().toEpochMilli()))

        val all = repository.summary(range(StatsPeriod.ALL, 0))
        val week = repository.summary(range(StatsPeriod.WEEK, 0))
        assertEquals(5, all.plays)
        assertEquals(1, week.plays)
        assertEquals("Live", week.songs.single().name)
    }

    @Test
    fun importingAnotherMostPlayedListReplacesTheBaseline() = runBlocking {
        repository.importCsv(listOf(StatsCsvImport.Parsed.MostPlayed(listOf(row("A", 4)))))
        repository.importCsv(listOf(StatsCsvImport.Parsed.MostPlayed(listOf(row("B", 2)))))
        assertEquals(2, dao.events.size)
        assertEquals(setOf("B"), dao.events.map { it.title }.toSet())
    }

    @Test
    fun severalMostPlayedFilesAtOnceAreAddedTogether() = runBlocking {
        val january = StatsCsvImport.Parsed.MostPlayed(listOf(row("A", 3)))
        val february = StatsCsvImport.Parsed.MostPlayed(listOf(row("A", 2), row("B", 1)))
        repository.importCsv(listOf(january, february))

        val songs = repository.summary(range(StatsPeriod.ALL, 0)).songs
        assertEquals(5, songs.first { it.name == "A" }.plays)
        assertEquals(1, songs.first { it.name == "B" }.plays)
    }

    @Test
    fun importingDatedPlaysKeepsTheBaseline() = runBlocking {
        repository.importCsv(listOf(StatsCsvImport.Parsed.MostPlayed(listOf(row("Old", 2)))))
        repository.importCsv(
            listOf(StatsCsvImport.Parsed.History(listOf(play("New", startedAt = 5_000))))
        )
        assertEquals(3, dao.events.size)
    }

    @Test
    fun aBackupWithABaselineRestoresItInPlaceOfTheCurrentOne() = runBlocking {
        repository.importCsv(listOf(StatsCsvImport.Parsed.MostPlayed(listOf(row("Gone", 9)))))
        val backup =
            StatsCsvImport.Parsed.History(
                listOf(
                    play("Kept", startedAt = PlayEvent.BASELINE_TIME),
                    play("Dated", startedAt = 5_000),
                )
            )
        repository.importCsv(listOf(backup))
        assertEquals(setOf("Kept", "Dated"), dao.events.map { it.title }.toSet())
    }
}
