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
    /** Tells the item apart from the others of its kind, for [summarizeItem]. */
    val key: String = "",
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
 * @param firstDayOfWeek The day a week starts on, a [Calendar] day. Monday by default.
 * @return The start (inclusive) and end (exclusive) in milliseconds since the unix epoch.
 */
fun statsRange(
    period: StatsPeriod,
    offset: Int,
    nowMs: Long = System.currentTimeMillis(),
    calendar: Calendar = Calendar.getInstance(),
    firstDayOfWeek: Int = Calendar.MONDAY,
): LongRange {
    if (period == StatsPeriod.ALL) {
        return PlayEvent.BASELINE_TIME until Long.MAX_VALUE
    }
    // The first day of the week is the setting of the user, not the regional one of the phone.
    calendar.firstDayOfWeek = firstDayOfWeek
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

/**
 * Collapse raw [PlayEvent]s into ranked lists. Each event counts as one play. Plays without an
 * artist (imported ones are often untagged) are counted for [unknownArtist], so the artists add up
 * to the total.
 */
fun summarize(
    events: List<PlayEvent>,
    limit: Int = 10,
    unknownArtist: String = "Unknown artist",
): ListeningStats {
    class Tally(val key: String, val name: String, val subtitle: String?) {
        var plays = 0
        var listenedMs = 0L
    }

    fun rank(tallies: Collection<Tally>) =
        tallies
            .sortedWith(compareByDescending<Tally> { it.plays }.thenByDescending { it.listenedMs })
            .take(limit)
            .map { RankedItem(it.name, it.subtitle, it.plays, it.listenedMs, it.key) }

    fun count(
        tallies: MutableMap<String, Tally>,
        key: String,
        name: String,
        subtitle: String?,
        event: PlayEvent,
    ) {
        val tally = tallies.getOrPut(key) { Tally(key, name, subtitle) }
        tally.plays++
        tally.listenedMs += event.listenedMs
    }

    val songs = LinkedHashMap<String, Tally>()
    val artists = LinkedHashMap<String, Tally>()
    val albums = LinkedHashMap<String, Tally>()
    for (event in events) {
        val artistLine = artistLine(event)
        count(songs, songKey(event), event.title, artistLine, event)
        for (artist in artistKeys(event, unknownArtist)) {
            count(artists, artist, artist, null, event)
        }
        albumKey(event)?.let { count(albums, it, event.album, artistLine, event) }
    }
    return ListeningStats(
        plays = events.size,
        listenedMs = events.sumOf { it.listenedMs },
        songs = rank(songs.values),
        artists = rank(artists.values),
        albums = rank(albums.values),
    )
}

private fun artistNames(event: PlayEvent) =
    event.artists.split(PlayEvent.ARTIST_SEPARATOR).filter { it.isNotBlank() }

/** The artists of a play as one line, for example "A, B". */
internal fun artistLine(event: PlayEvent) = artistNames(event).joinToString(", ")

/**
 * What tells the song of a play apart from other songs. Songs are matched by their text rather than
 * their UID, so a retagged file or an imported play still counts towards the same song.
 */
internal fun songKey(event: PlayEvent) =
    event.title + PlayEvent.ARTIST_SEPARATOR + artistLine(event) + event.album

/**
 * What tells the album of a play apart from other albums, or null if the play has no album. Albums
 * are keyed together with their artist, so two "Greatest Hits" stay separate.
 */
internal fun albumKey(event: PlayEvent) =
    if (event.album.isNotBlank()) {
        event.album + PlayEvent.ARTIST_SEPARATOR + artistLine(event)
    } else {
        null
    }

/** The artists a play counts for: its own, or [unknownArtist] if it has none. */
internal fun artistKeys(event: PlayEvent, unknownArtist: String) =
    artistNames(event).ifEmpty { listOf(unknownArtist) }.distinct()

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
    suspend fun summary(
        range: LongRange,
        unknownArtist: String = "Unknown artist",
    ): ListeningStats

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

    /** How many undated plays from before Noxio there are, see [PlayEvent.BASELINE_TIME]. */
    suspend fun undatedPlays(): Int = 0

    /**
     * Remove the undated plays from before Noxio, for example once weekly or monthly lists replace
     * them. Plays with a date are not touched.
     */
    suspend fun removeUndated() {}

    /**
     * Look closer at one artist, album or song of the stats, see [summarizeItem].
     *
     * @return Null if nothing was played of it.
     */
    suspend fun itemStats(
        category: StatsCategory,
        key: String,
        period: StatsPeriod,
        range: LongRange,
        unknownArtist: String = "Unknown artist",
    ): ItemStats? = null
}

class StatsRepositoryImpl @Inject constructor(private val dao: PlayEventDao) : StatsRepository {
    override suspend fun record(event: PlayEvent) = dao.insert(event)

    override suspend fun updateListened(id: Long, listenedMs: Long) =
        dao.updateListened(id, listenedMs)

    override suspend fun summary(range: LongRange, unknownArtist: String) =
        summarize(dao.between(range.first, range.last + 1), unknownArtist = unknownArtist)

    override suspend fun exportCsv(out: OutputStream) = StatsCsv.writeHistory(dao.all(), out)

    override suspend fun itemStats(
        category: StatsCategory,
        key: String,
        period: StatsPeriod,
        range: LongRange,
        unknownArtist: String,
    ) = summarizeItem(dao.all(), category, key, period, range, unknownArtist)

    override suspend fun undatedPlays() = dao.countBaseline()

    override suspend fun removeUndated() = dao.deleteBaseline()

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
                is StatsCsvImport.Parsed.MostPlayed -> {
                    val period = file.period
                    if (period == null) {
                        baseline += StatsCsvImport.baselineEvents(file.rows)
                    } else if (
                        period.isMonth &&
                            dao.between(period.startMs, period.endMs).any { it.estimated }
                    ) {
                        // Weekly lists say more than a monthly one, they are not overwritten.
                    } else {
                        // A week or month imported again replaces the first import of it.
                        dao.deleteEstimatedBetween(period.startMs, period.endMs)
                        dated += StatsCsvImport.spreadEvents(file.rows, period)
                    }
                }
            }
        }
        if (baseline.isNotEmpty()) {
            dao.deleteBaseline()
        }
        dao.insertAll(dated + baseline)
        return dated.size + baseline.size
    }
}
