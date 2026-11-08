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

    sealed interface Parsed {
        /** A history with a time for every play. */
        data class History(val events: List<PlayEvent>) : Parsed

        /** Play counts per song, without times. */
        data class MostPlayed(val rows: List<MostPlayedRow>) : Parsed
    }

    /** @return The parsed file, or null if it is not a stats file this can read. */
    fun parse(text: String): Parsed? {
        val rows = readRows(text)
        val header = rows.firstOrNull()?.mapIndexed { i, name -> name.trim().uppercase() to i }
        val columns = header?.toMap() ?: return null
        if ("TITLE" !in columns || "PLAY_COUNT" !in columns) {
            return null
        }
        val body = rows.drop(1)
        fun cell(row: List<String>, name: String) =
            columns[name]?.let { row.getOrNull(it) }?.trim().orEmpty().let {
                if (it == StatsCsv.UNKNOWN) "" else it
            }

        return if ("PLAYED_AT" in columns) {
            Parsed.History(
                body.mapNotNull { row ->
                    val playedAt =
                        runCatching { OffsetDateTime.parse(cell(row, "PLAYED_AT")).toInstant() }
                            .getOrNull() ?: return@mapNotNull null
                    val duration = cell(row, "DURATION_MS").toLongOrNull() ?: 0L
                    PlayEvent(
                        songUid = HISTORY_PREFIX + identity(row, ::cell),
                        title = cell(row, "TITLE"),
                        artists = splitArtists(cell(row, "ARTIST")),
                        album = cell(row, "ALBUM"),
                        startedAt = playedAt.toEpochMilli(),
                        listenedMs = cell(row, "LISTENED_MS").toLongOrNull() ?: duration,
                        durationMs = duration,
                        filePath = cell(row, "FILE_PATH"),
                        albumArtist = splitArtists(cell(row, "ALBUM_ARTIST")),
                        genre = cell(row, "GENRE"),
                        year = cell(row, "YEAR").toIntOrNull() ?: 0,
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
                }
            )
        }
    }

    /**
     * Turn play counts into individual plays, spread evenly over [range]. A list from Musicolet
     * says how often a song was played, but not when, so this is the least distorting choice for
     * the week and month views.
     */
    fun spreadOver(rows: List<MostPlayedRow>, range: LongRange): List<PlayEvent> {
        val length = (range.last + 1 - range.first).coerceAtLeast(1)
        return rows.flatMap { row ->
            (0 until row.plays).map { i ->
                PlayEvent(
                    songUid = IMPORT_PREFIX + row.filePath.ifEmpty { row.title + row.artist },
                    title = row.title,
                    artists = splitArtists(row.artist),
                    album = row.album,
                    startedAt = range.first + ((i + 0.5) / row.plays * length).toLong(),
                    listenedMs = row.durationMs,
                    durationMs = row.durationMs,
                    filePath = row.filePath,
                    albumArtist = splitArtists(row.albumArtist),
                    genre = row.genre,
                    year = row.year,
                )
            }
        }
    }

    /** What makes two plays the same one: the second it started in, and the song. */
    fun historyKey(event: PlayEvent) = "${event.startedAt / 1000}|${event.title}|${event.album}"

    /**
     * The time range of a month, for the plays of a most-played list. The range of the current
     * month ends now, so nothing is placed in the future.
     */
    fun monthRange(year: Int, month: Int, nowMs: Long = System.currentTimeMillis()): LongRange {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(year, month, 1)
        val start = calendar.timeInMillis
        calendar.add(Calendar.MONTH, 1)
        val end = minOf(calendar.timeInMillis, nowMs).coerceAtLeast(start + 1)
        return start until end
    }

    private fun identity(row: List<String>, cell: (List<String>, String) -> String) =
        cell(row, "FILE_PATH").ifEmpty { cell(row, "TITLE") + cell(row, "ARTIST") }

    private fun splitArtists(joined: String) =
        joined
            .split("; ")
            .filter { it.isNotBlank() }
            .joinToString(PlayEvent.ARTIST_SEPARATOR)

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

    /** Marks plays made up from a most-played list, so a re-import of that month replaces them. */
    const val IMPORT_PREFIX = "import:"
    private const val HISTORY_PREFIX = "csv:"
    private const val MAX_PLAYS_PER_SONG = 10_000
}
