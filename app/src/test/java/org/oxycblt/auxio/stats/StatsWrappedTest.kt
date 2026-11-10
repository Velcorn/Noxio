/*
 * Copyright (c) 2026 Auxio Project
 * StatsWrappedTest.kt is part of Auxio.
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

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsWrappedTest {
    private fun calendar() = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT)

    private fun at(year: Int, month: Int, day: Int) =
        calendar()
            .apply {
                clear()
                set(year, month - 1, day, 12, 0)
            }
            .timeInMillis

    private fun play(
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        startedAt: Long,
        listenedMs: Long = 60_000,
    ) =
        PlayEvent(
            songUid = "uid-$title",
            title = title,
            artists = artist,
            album = album,
            startedAt = startedAt,
            listenedMs = listenedMs,
            durationMs = 200_000,
        )

    private fun review(events: List<PlayEvent>, year: Int = 2026) =
        yearInReview(events, year, calendar = calendar())

    @Test
    fun anEmptyYearIsEmpty() {
        val review = review(emptyList())
        assertTrue(review.isEmpty)
        assertNull(review.topArtist)
        assertNull(review.busiestMonth)
        assertNull(review.busiestWeekday)
        assertNull(review.change)
        assertEquals(List(12) { 0 }, review.monthPlays)
    }

    @Test
    fun onlyThePlaysOfTheYearCount() {
        val events =
            listOf(
                play("A", startedAt = at(2025, 12, 31)),
                play("A", startedAt = at(2026, 1, 1)),
                play("B", startedAt = at(2026, 12, 31)),
                play("C", startedAt = at(2027, 1, 1)),
                play("D", startedAt = PlayEvent.BASELINE_TIME),
            )
        val review = review(events)
        assertEquals(2, review.plays)
        assertEquals(120_000L, review.listenedMs)
    }

    @Test
    fun theTopsAreTheMostPlayed() {
        val events =
            List(3) { play("Hit", artist = "Star", album = "Big", startedAt = at(2026, 3, 1)) } +
                List(2) {
                    play("Other", artist = "Band", album = "Small", startedAt = at(2026, 3, 2))
                }
        val review = review(events)
        assertEquals("Star", review.topArtist?.name)
        assertEquals("Big", review.topAlbum?.name)
        assertEquals(listOf("Hit", "Other"), review.topSongs.map { it.name })
    }

    @Test
    fun theBusiestMonthHasTheMostListening() {
        val events =
            listOf(
                play("A", startedAt = at(2026, 2, 3), listenedMs = 100_000),
                play("A", startedAt = at(2026, 2, 4), listenedMs = 100_000),
                play("B", startedAt = at(2026, 10, 1), listenedMs = 500_000),
            )
        val review = review(events)
        assertEquals(Calendar.OCTOBER, review.busiestMonth)
        assertEquals(2, review.monthPlays[Calendar.FEBRUARY])
        assertEquals(1, review.monthPlays[Calendar.OCTOBER])
    }

    @Test
    fun theBusiestWeekdayOnlyUsesRealTimes() {
        // November 13th 2026 is a Friday, the 9th a Monday.
        val events =
            listOf(
                play("A", startedAt = at(2026, 11, 13)),
                play("A", startedAt = at(2026, 11, 20)),
            ) + List(5) { play("B", startedAt = at(2026, 11, 9)).copy(estimated = true) }
        val review = review(events)
        assertEquals(Calendar.FRIDAY, review.busiestWeekday)
        assertEquals(2, review.weekdayPlays[4])
        assertEquals(0, review.weekdayPlays[0])
        // Imported plays still count for the months.
        assertEquals(7, review.monthPlays[Calendar.NOVEMBER])
    }

    @Test
    fun onlyImportedPlaysHaveNoBusiestWeekday() {
        val events = listOf(play("A", startedAt = at(2026, 5, 4)).copy(estimated = true))
        assertNull(review(events).busiestWeekday)
    }

    @Test
    fun theChangeComparesWithTheYearBefore() {
        val events =
            listOf(
                play("A", startedAt = at(2025, 6, 1), listenedMs = 100_000),
                play("A", startedAt = at(2026, 6, 1), listenedMs = 150_000),
            )
        assertEquals(0.5f, review(events).change!!, 0.001f)
        assertNull(review(events, 2025).change)
    }

    @Test
    fun aYearRangeIsTheWholeYear() {
        val range = yearRange(2026, calendar())
        assertEquals(at(2026, 1, 1) - 12 * 3600_000L, range.first)
        assertEquals(at(2027, 1, 1) - 12 * 3600_000L, range.last + 1)
    }
}
