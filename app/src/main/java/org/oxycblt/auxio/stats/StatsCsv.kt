/*
 * Copyright (c) 2026 Auxio Project
 * StatsCsv.kt is part of Auxio.
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Writes the play history as CSV.
 *
 * Both files start with the ten columns of Musicolet's "Most played songs" export: `FILE_PATH`,
 * `TITLE`, `ARTIST`, `ALBUM`, `ALBUM_ARTIST`, `COMPOSER`, `GENRE`, `YEAR`, `DURATION_MS` and
 * `PLAY_COUNT`, with `<unknown>` for what is not known. Artist and album rankings can be derived
 * from that.
 * - [writeHistory] writes one row per play with a `PLAY_COUNT` of 1 and two extra columns,
 *   `PLAYED_AT` (ISO 8601) and `LISTENED_MS`. Nothing is lost, so it is the format for backups.
 * - [writeMostPlayed] writes one row per song with its number of plays, like Musicolet does.
 *
 * Several artists are joined with "; ". Files are UTF-8 and use RFC 4180 quoting.
 */
object StatsCsv {
    const val UNKNOWN = "<unknown>"
    const val MUSICOLET_HEADER =
        "FILE_PATH,TITLE,ARTIST,ALBUM,ALBUM_ARTIST,COMPOSER,GENRE,YEAR,DURATION_MS,PLAY_COUNT"
    const val HISTORY_HEADER = "$MUSICOLET_HEADER,PLAYED_AT,LISTENED_MS"

    fun writeHistory(
        events: List<PlayEvent>,
        out: OutputStream,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val writer = out.bufferedWriter(Charsets.UTF_8)
        writer.write(HISTORY_HEADER)
        writer.write("\n")
        for (event in events) {
            val time =
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                    Instant.ofEpochMilli(event.startedAt).atZone(zone).withNano(0)
                )
            writer.write(row(songColumns(event, plays = 1) + listOf(time, "${event.listenedMs}")))
        }
        writer.flush()
    }

    fun writeMostPlayed(events: List<PlayEvent>, out: OutputStream) {
        val writer = out.bufferedWriter(Charsets.UTF_8)
        writer.write(MUSICOLET_HEADER)
        writer.write("\n")
        val songs = events.groupBy { songKey(it) }.values
        songs
            .map { plays -> plays.first() to plays.size }
            .sortedWith(
                compareByDescending<Pair<PlayEvent, Int>> { it.second }.thenBy { it.first.title }
            )
            .forEach { (event, plays) -> writer.write(row(songColumns(event, plays))) }
        writer.flush()
    }

    private fun songKey(event: PlayEvent) = listOf(event.title, event.artists, event.album)

    private fun songColumns(event: PlayEvent, plays: Int): List<String> =
        listOf(
            event.filePath.ifEmpty { UNKNOWN },
            event.title,
            joinArtists(event.artists).ifEmpty { UNKNOWN },
            event.album.ifEmpty { UNKNOWN },
            joinArtists(event.albumArtist).ifEmpty { UNKNOWN },
            // Noxio does not read composers.
            UNKNOWN,
            event.genre.ifEmpty { UNKNOWN },
            event.year.toString(),
            event.durationMs.toString(),
            plays.toString(),
        )

    private fun joinArtists(stored: String) =
        stored.split(PlayEvent.ARTIST_SEPARATOR).filter { it.isNotBlank() }.joinToString("; ")

    private fun row(fields: List<String>) = fields.joinToString(",") { field(it) } + "\n"

    /** Quote a field if it contains anything that would break the row. */
    fun field(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }
}
