/*
 * Copyright (c) 2026 Auxio Project
 * StatsItem.kt is part of Auxio.
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

/**
 * A closer look at one artist, album or song of the stats: how it did inside the period that is
 * shown, and a few numbers over all time.
 */
data class ItemStats(
    val category: StatsCategory,
    val name: String,
    /** The artists of an album or song. Null for an artist. */
    val subtitle: String?,
    /** How often it was played inside the period. */
    val plays: Int,
    /** How long it was listened to inside the period. */
    val listenedMs: Long,
    /** The part of all listening of the period that went to it, from 0 to 1. */
    val share: Float,
    /** On how many different days of the period it was played. Undated plays have no day. */
    val daysPlayed: Int,
    /** What one bar of [buckets] stands for. */
    val bucketUnit: BucketUnit,
    /** The plays inside the period over time, for a chart. Empty if no play has a date. */
    val buckets: List<Bucket>,
    /** The most played songs of an artist or album inside the period. Empty for a song. */
    val topSongs: List<RankedItem>,
    /** How often it was played over all time, with the plays from before Noxio. */
    val allTimePlays: Int,
    /** When it was played for the first time. Null if none of its plays has a date. */
    val firstPlayedMs: Long?,
    /** When it was played for the last time. Null if none of its plays has a date. */
    val lastPlayedMs: Long?,
    /**
     * How many of the plays inside the period come from imported counts and have a guessed time.
     */
    val estimatedPlays: Int = 0,
) {
    /** The plays that started in the day, month or year beginning at [startMs]. */
    data class Bucket(val startMs: Long, val plays: Int)

    enum class BucketUnit(val calendarField: Int) {
        DAY(Calendar.DAY_OF_MONTH),
        MONTH(Calendar.MONTH),
        YEAR(Calendar.YEAR),
    }
}

/** The longest history that is still charted month by month. Longer ones are charted by year. */
private const val MAX_MONTH_BUCKETS = 24

/**
 * Summarize the plays of one artist, album or song.
 *
 * @param events The whole listening history.
 * @param key The [RankedItem.key] of the item, of the kind [category].
 * @param period The period that is shown, with its [range] (see [statsRange]).
 * @param topSongs How many songs of an artist or album are listed.
 * @return Null if it was not played inside the period.
 */
fun summarizeItem(
    events: List<PlayEvent>,
    category: StatsCategory,
    key: String,
    period: StatsPeriod,
    range: LongRange,
    unknownArtist: String = "Unknown artist",
    calendar: Calendar = Calendar.getInstance(),
    nowMs: Long = System.currentTimeMillis(),
    topSongs: Int = 3,
): ItemStats? {
    fun isOfItem(event: PlayEvent) =
        when (category) {
            StatsCategory.ARTISTS -> key in artistKeys(event, unknownArtist)
            StatsCategory.ALBUMS -> albumKey(event) == key
            StatsCategory.SONGS -> songKey(event) == key
        }

    val periodEvents = events.filter { it.startedAt in range }
    val allTime = events.filter(::isOfItem)
    val inPeriod = periodEvents.filter(::isOfItem)
    val example = inPeriod.firstOrNull() ?: return null

    val listenedMs = inPeriod.sumOf { it.listenedMs }
    val periodListenedMs = periodEvents.sumOf { it.listenedMs }
    // Imported plays may have no listening time at all. Then the plays are compared instead.
    val share =
        if (periodListenedMs > 0) {
            listenedMs.toFloat() / periodListenedMs
        } else {
            inPeriod.size.toFloat() / periodEvents.size
        }

    // Plays with a guessed time count for the totals, but not for what needs a real time.
    val dated = inPeriod.filterNot { it.hasGuessedTime }.map { it.startedAt }
    val allTimeDated = allTime.filterNot { it.hasGuessedTime }.map { it.startedAt }
    val days =
        dated
            .map {
                calendar.timeInMillis = it
                calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
            }
            .toSet()

    val unit = bucketUnit(period, dated.minOrNull(), nowMs, calendar)
    return ItemStats(
        category = category,
        name =
            when (category) {
                StatsCategory.ARTISTS -> key
                StatsCategory.ALBUMS -> example.album
                StatsCategory.SONGS -> example.title
            },
        subtitle = if (category == StatsCategory.ARTISTS) null else artistLine(example),
        plays = inPeriod.size,
        listenedMs = listenedMs,
        share = share,
        daysPlayed = days.size,
        bucketUnit = unit,
        buckets =
            if (dated.isEmpty()) emptyList()
            else
                buckets(
                    dated,
                    bucketStarts(period, range, unit, dated.minOrNull(), nowMs, calendar),
                ),
        topSongs =
            if (category == StatsCategory.SONGS) emptyList()
            else summarize(inPeriod, limit = topSongs, unknownArtist = unknownArtist).songs,
        allTimePlays = allTime.size,
        firstPlayedMs = allTimeDated.minOrNull(),
        lastPlayedMs = allTimeDated.maxOrNull(),
        estimatedPlays = inPeriod.count { it.hasGuessedTime },
    )
}

/**
 * What a bar of the chart stands for: days in a week or month, months in a year. All time is
 * charted by month while the history is short, and by year after that.
 */
internal fun bucketUnit(
    period: StatsPeriod,
    firstPlayMs: Long?,
    nowMs: Long,
    calendar: Calendar,
): ItemStats.BucketUnit =
    when (period) {
        StatsPeriod.WEEK,
        StatsPeriod.MONTH -> ItemStats.BucketUnit.DAY
        StatsPeriod.YEAR -> ItemStats.BucketUnit.MONTH
        StatsPeriod.ALL -> {
            val months =
                if (firstPlayMs == null) {
                    0
                } else {
                    calendar.timeInMillis = firstPlayMs
                    val first = calendar.get(Calendar.YEAR) * 12 + calendar.get(Calendar.MONTH)
                    calendar.timeInMillis = nowMs
                    calendar.get(Calendar.YEAR) * 12 + calendar.get(Calendar.MONTH) - first + 1
                }
            if (months > MAX_MONTH_BUCKETS) ItemStats.BucketUnit.YEAR
            else ItemStats.BucketUnit.MONTH
        }
    }

/**
 * Where the bars of the chart begin. A week, month or year is covered as a whole, all time from the
 * first play until now.
 *
 * @return The starts in ascending order. Empty for all time without a dated play.
 */
internal fun bucketStarts(
    period: StatsPeriod,
    range: LongRange,
    unit: ItemStats.BucketUnit,
    firstPlayMs: Long?,
    nowMs: Long,
    calendar: Calendar,
): List<Long> {
    val end: Long
    if (period == StatsPeriod.ALL) {
        if (firstPlayMs == null) return emptyList()
        // Begin with the month or year of the first play.
        calendar.timeInMillis = firstPlayMs
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        if (unit == ItemStats.BucketUnit.YEAR) {
            calendar.set(Calendar.MONTH, Calendar.JANUARY)
        }
        end = maxOf(nowMs, firstPlayMs)
    } else {
        calendar.timeInMillis = range.first
        end = range.last
    }
    val starts = mutableListOf<Long>()
    while (calendar.timeInMillis <= end) {
        starts += calendar.timeInMillis
        calendar.add(unit.calendarField, 1)
    }
    return starts
}

/** Count the plays that started at [playTimes] into the bars that begin at [starts]. */
internal fun buckets(playTimes: List<Long>, starts: List<Long>): List<ItemStats.Bucket> {
    val counts = IntArray(starts.size)
    for (time in playTimes) {
        // The bar a play belongs to is the last one that begins before it.
        val found = starts.binarySearch(time)
        val index = if (found >= 0) found else -found - 2
        if (index in counts.indices) {
            counts[index]++
        }
    }
    return starts.mapIndexed { index, start -> ItemStats.Bucket(start, counts[index]) }
}

/** A file name for the image of the item called [name], for example "noxio-lil-peep.png". */
internal fun statsImageFileName(name: String): String {
    val words = name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(40)
    return if (words.isEmpty()) "noxio-stats.png" else "noxio-$words.png"
}
