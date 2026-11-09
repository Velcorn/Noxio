/*
 * Copyright (c) 2026 Auxio Project
 * StatsRepository.kt is part of Auxio.
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

import java.io.OutputStream
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
    /** Everything, including the undated plays imported from before Noxio. */
    ALL(Calendar.ERA),
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
    if (period == StatsPeriod.ALL) {
        return PlayEvent.BASELINE_TIME until Long.MAX_VALUE
    }
    // A week starts on Monday everywhere, whatever the regional setting of the phone says.
    calendar.firstDayOfWeek = Calendar.MONDAY
    calendar.timeInMillis = nowMs
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    when (period) {
        StatsPeriod.WEEK -> calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        StatsPeriod.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
        StatsPeriod.YEAR -> calendar.set(Calendar.DAY_OF_YEAR, 1)
        StatsPeriod.ALL -> {}
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

    fun count(
        tallies: MutableMap<String, Tally>,
        key: String,
        name: String,
        subtitle: String?,
        event: PlayEvent,
    ) {
        val tally = tallies.getOrPut(key) { Tally(name, subtitle) }
        tally.plays++
        tally.listenedMs += event.listenedMs
    }

    val songs = LinkedHashMap<String, Tally>()
    val artists = LinkedHashMap<String, Tally>()
    val albums = LinkedHashMap<String, Tally>()
    for (event in events) {
        val artistNames = event.artists.split(PlayEvent.ARTIST_SEPARATOR).filter { it.isNotBlank() }
        val artistLine = artistNames.joinToString(", ")

        // Songs are matched by their text rather than their UID, so a retagged file or an
        // imported play still counts towards the same song.
        val songKey = event.title + PlayEvent.ARTIST_SEPARATOR + artistLine + event.album
        count(songs, songKey, event.title, artistLine, event)
        for (artist in artistNames.distinct()) {
            count(artists, artist, artist, null, event)
        }
        // Albums are keyed together with their artist, so two "Greatest Hits" stay separate.
        if (event.album.isNotBlank()) {
            val albumKey = event.album + PlayEvent.ARTIST_SEPARATOR + artistLine
            count(albums, albumKey, event.album, artistLine, event)
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
    /**
     * Record a counted play.
     *
     * @return The id of the saved play, for [updateListened].
     */
    suspend fun record(event: PlayEvent): Long

    /** Change how long a saved play was listened to, as it goes on. */
    suspend fun updateListened(id: Long, listenedMs: Long)

    /** Summarize the plays inside [range]. */
    suspend fun summary(range: LongRange): ListeningStats

    /** Write the whole play history as CSV into [out]. The stream is not closed. */
    suspend fun exportCsv(out: OutputStream)

    /**
     * Add the plays of imported files to the history. Most-played lists have no dates, so they
     * become the undated "before Noxio" plays that only count towards [StatsPeriod.ALL], and they
     * replace the ones of an earlier import. Several lists given together are added up.
     *
     * @return How many plays were added.
     */
    suspend fun importCsv(files: List<StatsCsvImport.Parsed>): Int
}

class StatsRepositoryImpl @Inject constructor(private val dao: PlayEventDao) : StatsRepository {
    override suspend fun record(event: PlayEvent) = dao.insert(event)

    override suspend fun updateListened(id: Long, listenedMs: Long) =
        dao.updateListened(id, listenedMs)

    override suspend fun summary(range: LongRange) =
        summarize(dao.between(range.first, range.last + 1))

    override suspend fun exportCsv(out: OutputStream) = StatsCsv.writeHistory(dao.all(), out)

    override suspend fun importCsv(files: List<StatsCsvImport.Parsed>): Int {
        // Skip dated plays that are already there, so importing a backup twice is harmless.
        val known = dao.all().map { StatsCsvImport.historyKey(it) }.toHashSet()
        val dated = mutableListOf<PlayEvent>()
        val baseline = mutableListOf<PlayEvent>()
        for (file in files) {
            when (file) {
                is StatsCsvImport.Parsed.History ->
                    for (event in file.events) {
                        if (event.startedAt == PlayEvent.BASELINE_TIME) {
                            baseline += event
                        } else if (known.add(StatsCsvImport.historyKey(event))) {
                            dated += event
                        }
                    }
                is StatsCsvImport.Parsed.MostPlayed ->
                    baseline += StatsCsvImport.baselineEvents(file.rows)
            }
        }
        if (baseline.isNotEmpty()) {
            dao.deleteBaseline()
        }
        dao.insertAll(dated + baseline)
        return dated.size + baseline.size
    }
}
