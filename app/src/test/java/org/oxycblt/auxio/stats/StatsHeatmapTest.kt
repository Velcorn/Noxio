/*
 * Copyright (c) 2026 Auxio Project
 * StatsHeatmapTest.kt is part of Auxio.
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
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsHeatmapTest {
    private fun at(year: Int, month: Int, day: Int, zone: String = "UTC"): Long =
        Calendar.getInstance(TimeZone.getTimeZone(zone))
            .apply {
                set(year, month - 1, day, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            .timeInMillis

    @Test
    fun dailyTotalsSumsLocalDatesAcrossMidnight() {
        val zone = TimeZone.getTimeZone("Europe/Berlin")
        val c = Calendar.getInstance(zone)
        val totals =
            dailyTotals(
                listOf(
                    PlayTime(at(2026, 3, 29, "Europe/Berlin"), 10),
                    PlayTime(at(2026, 3, 29, "Europe/Berlin") + 60_000, 20),
                ),
                c,
            )
        assertEquals(30L, totals.values.single())
    }

    @Test
    fun levelsAreDeterministicAndBounded() {
        assertEquals(
            listOf(1, 2, 3, 4),
            heatLevels(mapOf(1L to 10, 2L to 20, 3L to 30, 4L to 10000)).values.toList(),
        )
        assertEquals(1, heatLevels(mapOf(1L to 5)).values.single())
    }

    @Test
    fun weeksAreMondayFirstAndHideFutureDays() {
        val now = at(2026, 1, 1)
        val weeks =
            heatmapWeeks(emptyMap(), now, now, Calendar.getInstance(TimeZone.getTimeZone("UTC")))
        assertEquals(1, weeks.size)
        assertEquals(7, weeks.single().days.size)
        assertTrue(weeks.single().days.any { it == null })
        assertTrue(heatmapWeeks(emptyMap(), null, now, Calendar.getInstance()).isEmpty())
    }

    private fun utc() = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

    private fun dayStart(time: Long) = time - time % 86_400_000L

    @Test
    fun monthsRunFromTheFirstPlayToNowWithEmptyMonthsIncluded() {
        val totals =
            mapOf(dayStart(at(2025, 11, 3)) to 60_000L, dayStart(at(2026, 1, 20)) to 120_000L)
        val months = heatPeriods(totals, StatsPeriod.MONTH, at(2025, 11, 3), at(2026, 2, 10), utc())
        assertEquals(4, months.size)
        assertEquals(listOf(60_000L, 0L, 120_000L, 0L), months.map { it.totalMs })
        assertEquals(dayStart(at(2025, 11, 1)), months.first().startMs)
        assertEquals(0, months[1].level)
        assertTrue(months[2].level > months[0].level)
    }

    @Test
    fun yearsAddUpTheirDays() {
        val totals =
            mapOf(
                dayStart(at(2024, 12, 31)) to 1_000L,
                dayStart(at(2025, 1, 1)) to 2_000L,
                dayStart(at(2025, 7, 1)) to 3_000L,
            )
        val years = heatPeriods(totals, StatsPeriod.YEAR, at(2024, 12, 31), at(2026, 3, 1), utc())
        assertEquals(listOf(1_000L, 5_000L, 0L), years.map { it.totalMs })
    }

    @Test
    fun periodsAreEmptyWithoutPlaysOrForWeeks() {
        assertTrue(
            heatPeriods(emptyMap(), StatsPeriod.MONTH, null, at(2026, 1, 1), utc()).isEmpty(),
        )
        assertTrue(
            heatPeriods(emptyMap(), StatsPeriod.WEEK, at(2025, 1, 1), at(2026, 1, 1), utc())
                .isEmpty(),
        )
    }

    @Test
    fun aWeekAcrossTheClockChangeHasSevenDistinctDays() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"))
        // 2026-03-29 is the switch to summer time in Berlin.
        val now = at(2026, 4, 1, "Europe/Berlin")
        val weeks = heatmapWeeks(emptyMap(), at(2026, 3, 23, "Europe/Berlin"), now, calendar)
        val dstWeek = weeks.last()
        val days = dstWeek.days.filterNotNull().map { it.dayMs }
        assertEquals(7, days.distinct().size)
        days.forEach { day ->
            calendar.timeInMillis = day
            assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
        }
    }
}
