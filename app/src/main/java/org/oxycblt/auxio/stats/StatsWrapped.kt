/*
 * Copyright (c) 2026 Auxio Project
 * StatsWrapped.kt is part of Auxio.
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
 * The numbers of a year in review ("Wrapped").
 *
 * @param year The year, for example 2026.
 * @param range Its start (inclusive) and end (exclusive), see [statsRange].
 * @param previousListenedMs How long was listened in the year before, to compare with. 0 if nothing
 *   was played then.
 * @param monthPlays The plays of each month, January first. Imported plays count, they are known to
 *   their week.
 * @param busiestMonth The month with the most listening time, 0 for January. Null without a play.
 * @param weekdayPlays The plays of each weekday, Monday first. Only plays with a real time count.
 * @param busiestWeekday The weekday with the most plays, as a [Calendar] day. Null if no play has a
 *   real time.
 */
data class YearInReview(
    val year: Int,
    val range: LongRange,
    val plays: Int,
    val listenedMs: Long,
    /** How many different songs were played. */
    val songs: Int,
    /** How many different artists were played. */
    val artists: Int,
    val previousListenedMs: Long,
    val topArtist: RankedItem?,
    val topAlbum: RankedItem?,
    val topSongs: List<RankedItem>,
    val monthPlays: List<Int>,
    val busiestMonth: Int?,
    val weekdayPlays: List<Int>,
    val busiestWeekday: Int?,
) {
    val isEmpty: Boolean
        get() = plays == 0

    /**
     * How much more (positive) or less (negative) was listened than in the year before, as a
     * fraction: 0.12 for 12% more. Null if nothing was played the year before.
     */
    val change: Float?
        get() =
            if (previousListenedMs > 0) {
                (listenedMs - previousListenedMs).toFloat() / previousListenedMs
            } else {
                null
            }
}

/**
 * Sum up the listening of [year].
 *
 * @param events The whole listening history.
 * @param calendar Used for the months and weekdays, in the time zone of the user.
 */
fun yearInReview(
    events: List<PlayEvent>,
    year: Int,
    unknownArtist: String = "Unknown artist",
    calendar: Calendar = Calendar.getInstance(),
    topSongs: Int = 5,
): YearInReview {
    val range = yearRange(year, calendar)
    val previous = yearRange(year - 1, calendar)
    val inYear = events.filter { it.startedAt in range }
    val summary = summarize(inYear, limit = Int.MAX_VALUE, unknownArtist = unknownArtist)

    val monthPlays = IntArray(12)
    val monthListened = LongArray(12)
    val weekdayPlays = IntArray(7)
    for (event in inYear) {
        calendar.timeInMillis = event.startedAt
        val month = calendar.get(Calendar.MONTH)
        monthPlays[month]++
        monthListened[month] += event.listenedMs
        // The day of an imported play is only a guess.
        if (!event.hasGuessedTime) {
            weekdayPlays[(calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7]++
        }
    }
    val busiestWeekdayIndex = weekdayPlays.indices.maxByOrNull { weekdayPlays[it] }
    return YearInReview(
        year = year,
        range = range,
        plays = summary.plays,
        listenedMs = summary.listenedMs,
        songs = summary.songs.size,
        artists = summary.artists.size,
        previousListenedMs = events.filter { it.startedAt in previous }.sumOf { it.listenedMs },
        topArtist = summary.artists.firstOrNull(),
        topAlbum = summary.albums.firstOrNull(),
        topSongs = summary.songs.take(topSongs),
        monthPlays = monthPlays.toList(),
        busiestMonth =
            if (inYear.isEmpty()) null
            else
                monthListened.indices.maxWith(compareBy({ monthListened[it] }, { monthPlays[it] })),
        weekdayPlays = weekdayPlays.toList(),
        busiestWeekday =
            busiestWeekdayIndex?.takeIf { weekdayPlays[it] > 0 }?.let { (it + 1) % 7 + 1 },
    )
}

/** The start (inclusive) and end (exclusive) of [year] in the time zone of [calendar]. */
internal fun yearRange(year: Int, calendar: Calendar): LongRange {
    calendar.clear()
    calendar.set(year, Calendar.JANUARY, 1)
    val start = calendar.timeInMillis
    calendar.add(Calendar.YEAR, 1)
    return start until calendar.timeInMillis
}
