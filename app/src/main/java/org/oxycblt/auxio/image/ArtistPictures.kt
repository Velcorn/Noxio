/*
 * Copyright (c) 2026 Auxio Project
 * ArtistPictures.kt is part of Auxio.
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
 
package org.oxycblt.auxio.image

/**
 * Noxio: the album whose cover stands for an artist, chosen in the artist's menu. Stored as one
 * line per artist, "artist UID<TAB>album UID" (UIDs hold neither tabs nor line breaks). An artist
 * without a choice shows the collage of their covers.
 */
object ArtistPictures {
    fun decode(encoded: String?): Map<String, String> =
        encoded
            .orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size == 2 && parts.all(String::isNotBlank)) parts[0] to parts[1] else null
            }
            .toMap()

    fun encode(choices: Map<String, String>): String =
        choices.entries.joinToString("\n") { (artist, album) -> "$artist\t$album" }

    fun choose(
        choices: Map<String, String>,
        artistUid: String,
        albumUid: String?,
    ): Map<String, String> =
        choices.toMutableMap().apply {
            if (albumUid == null) remove(artistUid) else put(artistUid, albumUid)
        }

    fun resolve(
        choices: Map<String, String>,
        artistUid: String,
        albumUids: Collection<String>,
    ): String? = choices[artistUid]?.takeIf { it in albumUids }
}
