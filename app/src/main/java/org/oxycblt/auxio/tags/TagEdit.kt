/*
 * Copyright (c) 2026 Auxio Project
 * TagEdit.kt is part of Auxio.
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
 
package org.oxycblt.auxio.tags

import org.oxycblt.musikr.Song
import org.oxycblt.musikr.metadata.TagWriter
import org.oxycblt.musikr.tag.Date
import org.oxycblt.musikr.tag.Name

/** One tag of a song that can be edited, with the TagLib property it is written to. */
enum class TagField(val key: String) {
    TITLE(TagWriter.TITLE),
    ARTIST(TagWriter.ARTIST),
    ALBUM(TagWriter.ALBUM),
    ALBUM_ARTIST(TagWriter.ALBUM_ARTIST),
    TRACK(TagWriter.TRACK),
    DATE(TagWriter.DATE),
}

/** What came of checking an edit. */
sealed interface TagEditResult {
    /** The fields to write, see [TagWriter.write]. Empty if nothing changed. */
    data class Write(val fields: Map<String, List<String>>) : TagEditResult

    /** These fields hold something that cannot be written. */
    data class Invalid(val fields: Set<TagField>) : TagEditResult
}

/** Noxio: editing the tags of a song, as text. */
object TagEdit {
    private fun delimiter(separator: String) =
        if (separator == ";" || separator == ",") "$separator " else " $separator "

    /** The tags of [song] as the editor shows them. Unknown names are empty. */
    fun of(song: Song, separator: String): Map<TagField, String> =
        mapOf(
            TagField.TITLE to song.name.text(),
            TagField.ARTIST to song.artists.joinToString(delimiter(separator)) { it.name.text() },
            TagField.ALBUM to song.album.name.text(),
            TagField.ALBUM_ARTIST to
                song.album.artists.joinToString(delimiter(separator)) { it.name.text() },
            TagField.TRACK to (song.track?.toString() ?: ""),
            TagField.DATE to (song.date?.toString() ?: ""),
        )

    private fun Name.text() = (this as? Name.Known)?.raw ?: ""

    /**
     * Check what was edited: the fields that differ from [before] (after trimming) and are valid. A
     * field that was emptied is removed from the file.
     */
    fun changes(
        before: Map<TagField, String>,
        after: Map<TagField, String>,
        separator: String,
    ): TagEditResult {
        val fields = LinkedHashMap<String, List<String>>()
        val invalid = mutableSetOf<TagField>()
        for (field in TagField.entries) {
            val old = before[field]?.trim() ?: ""
            val new = after[field]?.trim() ?: old
            if (new == old) continue
            val values = if (new.isEmpty()) emptyList() else valuesOf(field, new, separator)
            if (values == null) {
                invalid.add(field)
            } else {
                fields[field.key] = values
            }
        }
        return if (invalid.isEmpty()) TagEditResult.Write(fields)
        else TagEditResult.Invalid(invalid)
    }

    /** What is written for the text [value] of [field], or null if it is not valid. */
    private fun valuesOf(field: TagField, value: String, separator: String): List<String>? =
        when (field) {
            TagField.ARTIST,
            TagField.ALBUM_ARTIST ->
                value.split(separator).map { it.trim() }.filter { it.isNotEmpty() }
            TagField.DATE -> Date.from(value)?.let { listOf(value) }
            TagField.TRACK -> value.toIntOrNull()?.takeIf { it > 0 }?.let { listOf(it.toString()) }
            TagField.TITLE,
            TagField.ALBUM -> listOf(value)
        }
}
