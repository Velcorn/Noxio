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
 * Writes the play history as CSV, one row per counted play, oldest first.
 *
 * Columns: `date_time` (ISO 8601 with the UTC offset of the device), `title`, `artist` (several
 * artists are joined with "; "), `album`, `duration_seconds` and `listened_seconds`. The file is
 * UTF-8 and follows RFC 4180 quoting, so spreadsheets and import scripts can read it.
 */
object StatsCsv {
    const val HEADER = "date_time,title,artist,album,duration_seconds,listened_seconds"

    fun write(events: List<PlayEvent>, out: OutputStream, zone: ZoneId = ZoneId.systemDefault()) {
        val writer = out.bufferedWriter(Charsets.UTF_8)
        writer.write(HEADER)
        writer.write("\r\n")
        for (event in events) {
            val time =
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                    Instant.ofEpochMilli(event.startedAt).atZone(zone).withNano(0)
                )
            val artists = event.artists.split(PlayEvent.ARTIST_SEPARATOR).filter { it.isNotBlank() }
            val row =
                listOf(
                    time,
                    event.title,
                    artists.joinToString("; "),
                    event.album,
                    (event.durationMs / 1000).toString(),
                    (event.listenedMs / 1000).toString(),
                )
            writer.write(row.joinToString(",") { field(it) })
            writer.write("\r\n")
        }
        writer.flush()
    }

    /** Quote a field if it contains anything that would break the row. */
    fun field(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }
}
