/*
 * Copyright (c) 2026 Auxio Project
 * StatsCsvImport.kt is part of Auxio.
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

import java.time.OffsetDateTime
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** Reads the CSV files written by [StatsCsv], and Musicolet's "Most played songs" exports. */
object StatsCsvImport {
    /** One song of a most-played list. */
    data class MostPlayedRow(
        val filePath: String,
        val title: String,
        val artist: String,
        val album: String,
        val albumArtist: String,
        val genre: String,
        val year: Int,
        val durationMs: Long,
        val plays: Int,
    )

    /**
     * The week or month a Musicolet export is for, named by a file like "Most played Songs • Week
     * 22
     * - 2026.csv". The plays of the file are spread over it.
     */
    data class Period(
        val isMonth: Boolean,
        val year: Int,
        /** The number of the week in the year, or of the month (1 is January). */
        val number: Int,
        val startMs: Long,
        val endMs: Long,
    )

    sealed interface Parsed {
        /** A history with a time for every play. */
        data class History(val events: List<PlayEvent>) : Parsed

        /** Play counts per song, without times. */
        data class MostPlayed(val rows: List<MostPlayedRow>, val period: Period? = null) : Parsed
    }

    /**
     * Read the week or month a Musicolet export is for from its file name: "Week 22 - 2026" for a
     * week, "Month 5 - 2026" or "May 2026" (also "May - 2026") for a month.
     *
     * @param firstDayOfWeek The day a week starts on, a [Calendar] day. A week that starts on
     *   Monday is counted like ISO 8601 does (week 1 has four days of the new year), any other like
     *   the American calendar (week 1 holds January 1st).
     * @return Null if the name does not say a week or month.
     */
    fun periodOfFileName(
        name: String,
        firstDayOfWeek: Int = Calendar.MONDAY,
        zone: TimeZone = TimeZone.getDefault(),
    ): Period? {
        val calendar = GregorianCalendar(zone)
        calendar.clear()
        WEEK_NAME.find(name)?.let { match ->
            val number = match.groupValues[1].toInt()
            val year = match.groupValues[2].toInt()
            if (number !in 1..53) return null
            calendar.firstDayOfWeek = firstDayOfWeek
            calendar.minimalDaysInFirstWeek = if (firstDayOfWeek == Calendar.MONDAY) 4 else 1
            calendar.setWeekDate(year, number, firstDayOfWeek)
            val start = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, 7)
            return Period(false, year, number, start, calendar.timeInMillis)
        }
        val (month, year) =
            MONTH_NUMBER_NAME.find(name)?.let {
                it.groupValues[1].toInt() to it.groupValues[2].toInt()
            }
                ?: MONTH_WORD_NAME.find(name)?.let { match ->
                    val index = MONTHS.indexOfFirst {
                        match.groupValues[1].lowercase().startsWith(it)
                    }
                    if (index < 0) null else index + 1 to match.groupValues[2].toInt()
                }
                ?: return null
        if (month !in 1..12) return null
        calendar.set(year, month - 1, 1)
        val start = calendar.timeInMillis
        calendar.add(Calendar.MONTH, 1)
        return Period(true, year, month, start, calendar.timeInMillis)
    }

    /**
     * @param fileName The name of the file, which names the week of a Musicolet export.
     * @return The parsed file, or null if it is not a stats file this can read.
     */
    fun parse(
        text: String,
        fileName: String? = null,
        firstDayOfWeek: Int = Calendar.MONDAY,
    ): Parsed? {
        val rows = readRows(text)
        val header = rows.firstOrNull()?.mapIndexed { i, name -> name.trim().uppercase() to i }
        val columns = header?.toMap() ?: return null
        if ("TITLE" !in columns || "PLAY_COUNT" !in columns) {
            return null
        }
        val body = rows.drop(1)
        fun cell(row: List<String>, name: String): String {
            val value = columns[name]?.let { row.getOrNull(it) }?.trim().orEmpty()
            return if (value == StatsCsv.UNKNOWN) "" else value
        }

        return if ("PLAYED_AT" in columns) {
            Parsed.History(
                body.mapNotNull { row ->
                    // An empty time is a play from before Noxio (see PlayEvent.BASELINE_TIME).
                    val playedAt =
                        cell(row, "PLAYED_AT").let { raw ->
                            if (raw.isEmpty()) {
                                PlayEvent.BASELINE_TIME
                            } else {
                                runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
                                    .getOrNull() ?: return@mapNotNull null
                            }
                        }
                    val duration = cell(row, "DURATION_MS").toLongOrNull() ?: 0L
                    PlayEvent(
                        songUid = HISTORY_PREFIX + identity(row, ::cell),
                        title = cell(row, "TITLE"),
                        artists = splitArtists(cell(row, "ARTIST")),
                        album = cell(row, "ALBUM"),
                        startedAt = playedAt,
                        listenedMs = cell(row, "LISTENED_MS").toLongOrNull() ?: duration,
                        durationMs = duration,
                        filePath = cell(row, "FILE_PATH"),
                        albumArtist = splitArtists(cell(row, "ALBUM_ARTIST")),
                        genre = cell(row, "GENRE"),
                        year = cell(row, "YEAR").toIntOrNull() ?: 0,
                        estimated = cell(row, "ESTIMATED") == "1",
                    )
                }
            )
        } else {
            Parsed.MostPlayed(
                body.mapNotNull { row ->
                    val plays = cell(row, "PLAY_COUNT").toIntOrNull()?.takeIf { it > 0 }
                    if (plays == null || cell(row, "TITLE").isEmpty()) {
                        null
                    } else {
                        MostPlayedRow(
                            filePath = cell(row, "FILE_PATH"),
                            title = cell(row, "TITLE"),
                            artist = cell(row, "ARTIST"),
                            album = cell(row, "ALBUM"),
                            albumArtist = cell(row, "ALBUM_ARTIST"),
                            genre = cell(row, "GENRE"),
                            year = cell(row, "YEAR").toIntOrNull() ?: 0,
                            durationMs = cell(row, "DURATION_MS").toLongOrNull() ?: 0L,
                            plays = plays.coerceAtMost(MAX_PLAYS_PER_SONG),
                        )
                    }
                },
                period = fileName?.let { periodOfFileName(it, firstDayOfWeek) },
            )
        }
    }

    /**
     * Turn the play counts of one week or month into plays that are spread evenly over it: a song
     * played five times in a week gets five plays a bit more than a day apart. Their time is a
     * guess, so they are [PlayEvent.estimated]. The same file always gives the same plays.
     */
    fun spreadEvents(rows: List<MostPlayedRow>, period: Period): List<PlayEvent> {
        val length = period.endMs - period.startMs
        return rows.flatMapIndexed { index, row ->
            // Songs are shifted against each other a little, so they do not all start at once.
            val shift = (index * SHIFT_MS) % DAY_MS
            (0 until row.plays).map { i ->
                PlayEvent(
                    songUid = IMPORT_PREFIX + row.filePath.ifEmpty { row.title + row.artist },
                    title = row.title,
                    artists = splitArtists(row.artist),
                    album = row.album,
                    startedAt = period.startMs + length * i / row.plays + shift,
                    listenedMs = row.durationMs,
                    durationMs = row.durationMs,
                    filePath = row.filePath,
                    albumArtist = splitArtists(row.albumArtist),
                    genre = row.genre,
                    year = row.year,
                    estimated = true,
                )
            }
        }
    }

    /**
     * Turn play counts into individual plays from before Noxio. A list from Musicolet says how
     * often a song was played, but not when, so these plays get no date ([PlayEvent.BASELINE_TIME])
     * and only show up in the all time stats.
     */
    fun baselineEvents(rows: List<MostPlayedRow>): List<PlayEvent> = rows.flatMap { row ->
        (0 until row.plays).map {
            PlayEvent(
                songUid = IMPORT_PREFIX + row.filePath.ifEmpty { row.title + row.artist },
                title = row.title,
                artists = splitArtists(row.artist),
                album = row.album,
                startedAt = PlayEvent.BASELINE_TIME,
                listenedMs = row.durationMs,
                durationMs = row.durationMs,
                filePath = row.filePath,
                albumArtist = splitArtists(row.albumArtist),
                genre = row.genre,
                year = row.year,
                estimated = true,
            )
        }
    }

    /** What makes two plays the same one: the second it started in, and the song. */
    fun historyKey(event: PlayEvent) = "${event.startedAt / 1000}|${event.title}|${event.album}"

    private fun identity(row: List<String>, cell: (List<String>, String) -> String) =
        cell(row, "FILE_PATH").ifEmpty { cell(row, "TITLE") + cell(row, "ARTIST") }

    private fun splitArtists(joined: String) =
        joined.split("; ").filter { it.isNotBlank() }.joinToString(PlayEvent.ARTIST_SEPARATOR)

    /** A CSV reader for RFC 4180 files: quoted fields, doubled quotes, line breaks in quotes. */
    fun readRows(text: String): List<List<String>> {
        val source = text.removePrefix("\uFEFF")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (quoted) {
                if (c == '"') {
                    if (source.getOrNull(i + 1) == '"') {
                        field.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> if (field.isEmpty()) quoted = true else field.append(c)
                    ',' -> {
                        row.add(field.toString())
                        field.clear()
                    }
                    '\r' -> {}
                    '\n' -> {
                        row.add(field.toString())
                        field.clear()
                        rows.add(row)
                        row = mutableListOf()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows.filter { cells -> cells.any { it.isNotEmpty() } }
    }

    /** Marks plays made up from a most-played list. */
    const val IMPORT_PREFIX = "import:"
    private const val HISTORY_PREFIX = "csv:"
    private const val MAX_PLAYS_PER_SONG = 10_000
    private const val SHIFT_MS = 97_000L
    private const val DAY_MS = 24L * 3600 * 1000
    private val WEEK_NAME = Regex("""Week\s*(\d{1,2})\s*[-–]\s*(\d{4})""", RegexOption.IGNORE_CASE)
    private val MONTH_NUMBER_NAME =
        Regex("""Month\s*(\d{1,2})\s*[-–]\s*(\d{4})""", RegexOption.IGNORE_CASE)
    private val MONTH_WORD_NAME = Regex("""\b([A-Za-z]{3,9})\.?\s*[-–,]?\s*(\d{4})\b""")
    private val MONTHS =
        listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
}
