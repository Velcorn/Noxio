/*
 * Copyright (c) 2026 Auxio Project
 * StatsHeatmap.kt is part of Auxio.
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

/** A dated play's contribution to the calendar. */
data class PlayTime(val startedAt: Long, val listenedMs: Long)

data class HeatDay(val dayMs: Long, val level: Int)

data class HeatWeek(val startMs: Long, val days: List<HeatDay?>)

/** Sum listening by local calendar day. */
fun dailyTotals(plays: List<PlayTime>, calendar: Calendar): Map<Long, Long> {
    val result = mutableMapOf<Long, Long>()
    for (play in plays) {
        calendar.timeInMillis = play.startedAt
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val day = calendar.timeInMillis
        result[day] = (result[day] ?: 0L) + play.listenedMs
    }
    return result
}

/** Map positive totals into four quartile levels; the highest total is always level 4. */
fun heatLevels(totals: Map<Long, Long>): Map<Long, Int> {
    val values = totals.values.filter { it > 0 }.sorted()
    if (values.isEmpty()) return totals.keys.associateWith { 0 }
    return totals.mapValues { (_, value) ->
        if (value <= 0) 0
        // By the share of periods at or below this one, so the busiest always gets the top level.
        else (values.count { it <= value } * 4 + values.size - 1) / values.size
    }
}

/** Monday-first weeks, newest first. Calendar arithmetic preserves local days across DST. */
fun heatmapWeeks(
    totals: Map<Long, Long>,
    firstMs: Long?,
    nowMs: Long,
    calendar: Calendar,
    firstDayOfWeek: Int = Calendar.MONDAY,
): List<HeatWeek> {
    if (firstMs == null) return emptyList()
    calendar.firstDayOfWeek = firstDayOfWeek
    fun dayStart(time: Long): Long {
        calendar.timeInMillis = time
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    val firstDay = dayStart(firstMs)
    val today = dayStart(nowMs)
    calendar.timeInMillis = today
    calendar.set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
    var weekStart = calendar.timeInMillis
    val levels = heatLevels(totals)
    val weeks = mutableListOf<HeatWeek>()
    while (true) {
        val start = weekStart
        val days =
            (0..6).map { index ->
                calendar.timeInMillis = start
                calendar.add(Calendar.DAY_OF_MONTH, index)
                val day = calendar.timeInMillis
                if (day < firstDay || day > today) null else HeatDay(day, levels[day] ?: 0)
            }
        weeks += HeatWeek(start, days)
        if (start <= firstDay) break
        calendar.timeInMillis = start
        calendar.add(Calendar.DAY_OF_MONTH, -7)
        weekStart = calendar.timeInMillis
    }
    return weeks
}

/** One week, month or year in the period picker. */
data class HeatPeriod(val startMs: Long, val totalMs: Long, val level: Int)

/**
 * Add up [totals] (by day) into periods from the one holding [firstMs] up to the one holding
 * [nowMs], oldest first. Periods without plays are included with level 0.
 */
fun heatPeriods(
    totals: Map<Long, Long>,
    period: StatsPeriod,
    firstMs: Long?,
    nowMs: Long,
    calendar: Calendar,
): List<HeatPeriod> {
    if (firstMs == null || period == StatsPeriod.ALL) {
        return emptyList()
    }
    calendar.firstDayOfWeek = Calendar.MONDAY
    calendar.minimalDaysInFirstWeek = 4
    fun periodStart(time: Long): Long {
        calendar.timeInMillis = time
        when (period) {
            StatsPeriod.YEAR -> calendar.set(Calendar.DAY_OF_YEAR, 1)
            StatsPeriod.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
            StatsPeriod.WEEK -> calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            StatsPeriod.ALL -> return time
        }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    val sums = mutableMapOf<Long, Long>()
    for ((day, ms) in totals) {
        val start = periodStart(day)
        sums[start] = (sums[start] ?: 0L) + ms
    }
    val starts = mutableListOf<Long>()
    var start = periodStart(firstMs)
    val last = periodStart(nowMs)
    while (start <= last) {
        starts += start
        calendar.timeInMillis = start
        calendar.add(period.calendarField, 1)
        start = calendar.timeInMillis
    }
    val levels = heatLevels(starts.associateWith { sums[it] ?: 0L })
    return starts.map { HeatPeriod(it, sums[it] ?: 0L, levels[it] ?: 0) }
}

/** ISO week number and its Thursday year for a Monday week start. */
fun isoWeek(startMs: Long, calendar: Calendar): Pair<Int, Int> {
    calendar.firstDayOfWeek = Calendar.MONDAY
    calendar.minimalDaysInFirstWeek = 4
    calendar.timeInMillis = startMs
    val number = calendar.get(Calendar.WEEK_OF_YEAR)
    calendar.add(Calendar.DAY_OF_MONTH, 3)
    return number to calendar.get(Calendar.YEAR)
}
