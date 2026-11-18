/*
 * Copyright (c) 2026 Auxio Project
 * NoxioFileName.kt is part of Auxio.
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

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The names of every file Noxio writes for the user: `Noxio_<What>_YYYY-MM-DD_HH-MM-SS.<ext>`, for
 * example `Noxio_Backup_2026-11-18_21-00-00.zip` or `Noxio_Lil-Peep_2026-11-18_21-00-00.png`.
 */
object NoxioFileName {
    private val FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    const val BACKUP = "Backup"
    const val STATS = "Stats"

    /** The timestamp part, to the second. */
    fun timestamp(time: LocalDateTime): String = FORMAT.format(time)

    fun of(what: String, extension: String, time: LocalDateTime = LocalDateTime.now()) =
        "Noxio_${what}_${timestamp(time)}.$extension"

    /**
     * Make [name] (of an artist, album or song) fit between the underscores: anything but letters
     * and digits becomes one `-`, and it is cut to 40 characters. [STATS] if nothing is left.
     */
    fun label(name: String): String =
        name.replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(40).trimEnd('-').ifEmpty {
            STATS
        }
}
