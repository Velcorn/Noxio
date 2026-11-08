/*
 * Copyright (c) 2026 Noxio Project
 * StatsRepository.kt is part of Noxio.
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
import javax.inject.Inject

/** What a ranked row in the stats refers to. */
data class RankedItem(
    val name: String,
    /** Extra line below the name, for example the artist of a song. Null if there is none. */
    val subtitle: String?,
    val plays: Int,
    val listenedMs: Long,
)

/** The summarized listening history of one period. */
data class ListeningStats(
    val plays: Int,
    val listenedMs: Long,
    val songs: List<RankedItem>,
    val artists: List<RankedItem>,
    val albums: List<RankedItem>,
) {
    val isEmpty: Boolean
        get() = plays == 0
}

/** The calendar unit the stats are shown for. */
enum class StatsPeriod(val calendarField: Int) {
    WEEK(Calendar.WEEK_OF_YEAR),
    MONTH(Calendar.MONTH),
    YEAR(Calendar.YEAR),
}

/**
 * Compute the calendar range of a [period], shifted by [offset] periods (0 is the current one, -1
 * the one before).
 *
 * @return The start (inclusive) and end (exclusive) in milliseconds since the unix epoch.
 */
fun statsRange(
    period: StatsPeriod,
    offset: Int,
    nowMs: Long = System.currentTimeMillis(),
    calendar: Calendar = Calendar.getInstance(),
): LongRange {
    calendar.timeInMillis = nowMs
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    when (period) {
        StatsPeriod.WEEK -> calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        StatsPeriod.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
        StatsPeriod.YEAR -> calendar.set(Calendar.DAY_OF_YEAR, 1)
    }
    calendar.add(period.calendarField, offset)
    val start = calendar.timeInMillis
    calendar.add(period.calendarField, 1)
    return start until calendar.timeInMillis
}

/** Collapse raw [PlayEvent]s into ranked lists. Each event counts as one play. */
fun summarize(events: List<PlayEvent>, limit: Int = 10): ListeningStats {
    class Tally(val name: String, val subtitle: String?) {
        var plays = 0
        var listenedMs = 0L
    }

    fun rank(tallies: Collection<Tally>) =
        tallies
            .sortedWith(compareByDescending<Tally> { it.plays }.thenByDescending { it.listenedMs })
            .take(limit)
            .map { RankedItem(it.name, it.subtitle, it.plays, it.listenedMs) }

    val songs = LinkedHashMap<String, Tally>()
    val artists = LinkedHashMap<String, Tally>()
    val albums = LinkedHashMap<String, Tally>()
    for (event in events) {
        val artistNames = event.artists.split(PlayEvent.ARTIST_SEPARATOR).filter { it.isNotBlank() }
        val artistLine = artistNames.joinToString(", ")

        songs.getOrPut(event.songUid) { Tally(event.title, artistLine) }.apply {
            plays++
            listenedMs += event.listenedMs
        }
        for (artist in artistNames.distinct()) {
            artists.getOrPut(artist) { Tally(artist, null) }.apply {
                plays++
                listenedMs += event.listenedMs
            }
        }
        // Albums are keyed together with their artist, so two "Greatest Hits" stay separate.
        albums.getOrPut(event.album + PlayEvent.ARTIST_SEPARATOR + artistLine) {
                Tally(event.album, artistLine)
            }
            .apply {
                plays++
                listenedMs += event.listenedMs
            }
    }
    return ListeningStats(
        plays = events.size,
        listenedMs = events.sumOf { it.listenedMs },
        songs = rank(songs.values),
        artists = rank(artists.values),
        albums = rank(albums.values),
    )
}

interface StatsRepository {
    /** Record a counted play. */
    suspend fun record(event: PlayEvent)

    /** Summarize the plays inside [range]. */
    suspend fun summary(range: LongRange): ListeningStats
}

class StatsRepositoryImpl @Inject constructor(private val dao: PlayEventDao) : StatsRepository {
    override suspend fun record(event: PlayEvent) = dao.insert(event)

    override suspend fun summary(range: LongRange) =
        summarize(dao.between(range.first, range.last + 1))
}
