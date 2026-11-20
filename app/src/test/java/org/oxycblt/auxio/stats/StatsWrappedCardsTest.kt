/*
 * Copyright (c) 2026 Auxio Project
 * StatsWrappedCardsTest.kt is part of Auxio.
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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsWrappedCardsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

    private fun event(year: Int, title: String = "Song", hour: Int = 8): PlayEvent {
        val time =
            calendar
                .apply {
                    clear()
                    set(year, Calendar.JUNE, 1, hour, 0)
                }
                .timeInMillis
        return PlayEvent(
            songUid = "$year-$title",
            title = title,
            artists = "Artist",
            album = "Album",
            startedAt = time,
            listenedMs = 60_000,
            durationMs = 200_000,
        )
    }

    private fun itemStats(item: RankedItem) =
        ItemStats(
            category = StatsCategory.ARTISTS,
            name = item.name,
            subtitle = null,
            plays = item.plays,
            listenedMs = item.listenedMs,
            share = 1f,
            daysPlayed = 1,
            bucketUnit = ItemStats.BucketUnit.YEAR,
            buckets = emptyList(),
            topSongs = emptyList(),
            allTimePlays = item.plays,
            firstPlayedMs = null,
            lastPlayedMs = null,
        )

    @Test
    fun yearlyCardsHaveThePlannedOrderAndSkipClockWithoutRealTimes() {
        val history =
            listOf(
                event(2026).copy(estimated = true),
                event(2026, "Night", 22).copy(estimated = true),
            )
        val review = yearInReview(history, 2026, calendar = calendar)
        val cards =
            wrappedCards(
                context,
                StatsViewModel.Wrapped(review, itemStats(review.topArtist!!)),
            )
        assertEquals(
            listOf("2026", "Artist", "2026-Songs", "2026-Summary"),
            cards.map { it.first },
        )
        assertFalse(cards.any { it.first.contains("Album") })
        val withClockReview = yearInReview(listOf(event(2026)), 2026, calendar = calendar)
        val withClock =
            wrappedCards(
                context,
                StatsViewModel.Wrapped(withClockReview, itemStats(withClockReview.topArtist!!)),
            )
        assertEquals(
            listOf("2026", "Artist", "2026-Songs", "2026-Clock", "2026-Summary"),
            withClock.map { it.first },
        )
        assertTrue(withClock[3].second.clockHours.isNotEmpty())
    }

    @Test
    fun allTimeCardsLimitYearsAndKeepRecentYearsFirst() {
        val history = (2018..2026).map { event(it, "Song$it", it % 24) }
        val review = allTimeReview(history, calendar = calendar)
        val artist = review.topArtist!!
        val cards = allTimeCards(context, StatsViewModel.AllTimeWrapped(review, itemStats(artist)))
        assertEquals(
            listOf(
                "All-time",
                "All-time-Years",
                "Artist",
                "All-time-Songs",
                "All-time-Clock",
                "All-time-Summary",
            ),
            cards.map { it.first },
        )
        assertEquals(8, cards.first().second.bars.size)
        assertEquals(
            listOf("2026", "2025", "2024", "2023", "2022"),
            cards[1].second.rows.map { it.name },
        )
        assertFalse(cards[1].second.rankedRows)
        assertTrue(cards.last().second.rankedRows)
        assertEquals(6, cards.last().second.yearGrid.size)
    }
}
