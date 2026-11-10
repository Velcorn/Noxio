/*
 * Copyright (c) 2026 Auxio Project
 * StatsFormatTest.kt is part of Auxio.
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
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class StatsFormatTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun minutes(n: Long) = n * 60_000L

    @Test
    fun aShareIsAWholePercentAndNeverNothing() {
        assertEquals("25%", StatsFormat.percent(0.25f, Locale.US))
        assertEquals("100%", StatsFormat.percent(1f, Locale.US))
        assertEquals("1%", StatsFormat.percent(0.0001f, Locale.US))
    }

    @Test
    fun aDayIsWrittenWithItsYear() {
        val day = utc(2026, Calendar.NOVEMBER, 8)
        val text = StatsFormat.date(context, day)
        assertTrue(text, text.contains("2026") && text.contains("8"))
    }

    private fun utc(year: Int, month: Int, day: Int) =
        Calendar.getInstance(TimeZone.getDefault()).run {
            clear()
            set(year, month, day, 12, 0)
            timeInMillis
        }

    @Test
    fun aWeekNamesItsDaysAndAYearItsMonths() {
        val week = List(7) { utc(2026, Calendar.NOVEMBER, 9 + it) }
        assertEquals(
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
            StatsFormat.bucketLabels(StatsPeriod.WEEK, ItemStats.BucketUnit.DAY, week, Locale.US),
        )
        val year = List(12) { utc(2026, it, 1) }
        assertEquals(
            "JFMAMJJASOND".map { it.toString() },
            StatsFormat.bucketLabels(StatsPeriod.YEAR, ItemStats.BucketUnit.MONTH, year, Locale.US),
        )
    }

    @Test
    fun aMonthNumbersEverySeventhDay() {
        val month = List(30) { utc(2026, Calendar.NOVEMBER, 1 + it) }
        val labels =
            StatsFormat.bucketLabels(StatsPeriod.MONTH, ItemStats.BucketUnit.DAY, month, Locale.US)
        assertEquals(listOf("1", "8", "15", "22", "29"), labels.filterNotNull())
        assertEquals("8", labels[7])
    }

    @Test
    fun allTimeNamesItsTwoEnds() {
        val months = List(5) { utc(2026, Calendar.MARCH + it, 1) }
        assertEquals(
            listOf("Mar 2026", null, null, null, "Jul 2026"),
            StatsFormat.bucketLabels(
                StatsPeriod.ALL,
                ItemStats.BucketUnit.MONTH,
                months,
                Locale.US,
            ),
        )
        val years = List(3) { utc(2024 + it, Calendar.JANUARY, 1) }
        assertEquals(
            listOf("2024", null, "2026"),
            StatsFormat.bucketLabels(StatsPeriod.ALL, ItemStats.BucketUnit.YEAR, years, Locale.US),
        )
        assertEquals(
            listOf("2024"),
            StatsFormat.bucketLabels(
                StatsPeriod.ALL,
                ItemStats.BucketUnit.YEAR,
                years.take(1),
                Locale.US,
            ),
        )
    }

    @Test
    fun oneAndManyPlays() {
        assertEquals("1 play", StatsFormat.plays(context, 1))
        assertEquals("2 plays", StatsFormat.plays(context, 2))
        assertEquals("0 plays", StatsFormat.plays(context, 0))
    }

    @Test
    fun lessThanAMinuteIsSaidSo() {
        assertEquals("<1 min", StatsFormat.listened(context, 59_999))
        assertEquals("<1 min", StatsFormat.listened(context, 0))
    }

    @Test
    fun minutesWithoutHours() {
        assertEquals("1 min", StatsFormat.listened(context, minutes(1)))
        assertEquals("59 min", StatsFormat.listened(context, minutes(59)))
    }

    @Test
    fun hoursAndMinutes() {
        assertEquals("1 h 0 min", StatsFormat.listened(context, minutes(60)))
        assertEquals("2 h 5 min", StatsFormat.listened(context, minutes(125)))
        assertEquals("447 h 28 min", StatsFormat.listened(context, minutes(447 * 60 + 28)))
    }

    @Test
    fun allTimeAndYearLabels() {
        assertEquals("All time", StatsFormat.range(context, StatsPeriod.ALL, 0L..1L))
        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply { set(2024, 5, 15) }
        val label = StatsFormat.range(context, StatsPeriod.YEAR, calendar.timeInMillis..1L)
        assertEquals("2024", label)
    }

    @Test
    fun monthAndWeekLabelsShowTheYear() {
        val calendar = Calendar.getInstance().apply { set(2024, 10, 11, 12, 0) }
        val start = calendar.timeInMillis
        val end = start + 6 * 24 * 3_600_000L
        assertTrue(StatsFormat.range(context, StatsPeriod.MONTH, start..end).contains("2024"))
        assertTrue(StatsFormat.range(context, StatsPeriod.WEEK, start..end).contains("2024"))
    }
}
