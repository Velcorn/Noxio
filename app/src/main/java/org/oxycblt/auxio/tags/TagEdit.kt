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

import java.util.Locale
import kotlin.math.abs
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
    DATE(TagWriter.DATE),
    TRACK(TagWriter.TRACK),
    DISC(TagWriter.DISC),
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
    /** How several artists are shown in one field, and split again when saved. */
    const val SEPARATOR = "; "

    /** The tags of [song] as the editor shows them. Unknown names are empty. */
    fun of(song: Song): Map<TagField, String> =
        mapOf(
            TagField.TITLE to song.name.text(),
            TagField.ARTIST to song.artists.joinToString(SEPARATOR) { it.name.text() },
            TagField.ALBUM to song.album.name.text(),
            TagField.ALBUM_ARTIST to song.album.artists.joinToString(SEPARATOR) { it.name.text() },
            TagField.DATE to (song.date?.toString() ?: ""),
            TagField.TRACK to (song.track?.toString() ?: ""),
            TagField.DISC to (song.disc?.number?.toString() ?: ""),
        )

    private fun Name.text() = (this as? Name.Known)?.raw ?: ""

    /**
     * Check what was edited: the fields that differ from [before] (after trimming) and are valid. A
     * field that was emptied is removed from the file.
     */
    fun changes(before: Map<TagField, String>, after: Map<TagField, String>): TagEditResult {
        val fields = LinkedHashMap<String, List<String>>()
        val invalid = mutableSetOf<TagField>()
        for (field in TagField.entries) {
            val old = before[field]?.trim() ?: ""
            val new = after[field]?.trim() ?: old
            if (new == old) continue
            val values = if (new.isEmpty()) emptyList() else valuesOf(field, new)
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
    private fun valuesOf(field: TagField, value: String): List<String>? =
        when (field) {
            TagField.ARTIST,
            TagField.ALBUM_ARTIST ->
                value.split(SEPARATOR.trim()).map { it.trim() }.filter { it.isNotEmpty() }
            TagField.DATE -> Date.from(value)?.let { listOf(value) }
            TagField.TRACK,
            TagField.DISC -> value.toIntOrNull()?.takeIf { it > 0 }?.let { listOf(it.toString()) }
            TagField.TITLE,
            TagField.ALBUM -> listOf(value)
        }

    /** A gain as ReplayGain writes it: "-6.52 dB". */
    fun formatGain(db: Float): String = String.format(Locale.ROOT, "%.2f dB", db)

    /** A peak as ReplayGain writes it: "0.987654". */
    fun formatPeak(peak: Float): String = String.format(Locale.ROOT, "%.6f", peak)
}

/** Noxio: which measured ReplayGain values are written into a song's tags. */
object ReplayGainTags {
    /**
     * The fields to write for one song: the measured track and album values, each only where it is
     * the value the song plays with (no tag, or tags not preferred) and differs from the tag. The
     * peaks go with them when they are known.
     */
    fun fields(
        trackTag: Float?,
        albumTag: Float?,
        measuredTrack: Float?,
        measuredAlbum: Float?,
        trackPeak: Float?,
        albumPeak: Float?,
        preferTags: Boolean,
    ): Map<String, List<String>> {
        val fields = LinkedHashMap<String, List<String>>()
        if (measuredTrack != null && needed(trackTag, measuredTrack, preferTags)) {
            fields[TagWriter.TRACK_GAIN] = listOf(TagEdit.formatGain(measuredTrack))
            trackPeak?.let { fields[TagWriter.TRACK_PEAK] = listOf(TagEdit.formatPeak(it)) }
        }
        if (measuredAlbum != null && needed(albumTag, measuredAlbum, preferTags)) {
            fields[TagWriter.ALBUM_GAIN] = listOf(TagEdit.formatGain(measuredAlbum))
            albumPeak?.let { fields[TagWriter.ALBUM_PEAK] = listOf(TagEdit.formatPeak(it)) }
        }
        return fields
    }

    private fun needed(tag: Float?, measured: Float, preferTags: Boolean) =
        // 0 dB changes nothing, and is read back as no tag at all, so it is not written.
        abs(measured) >= 0.005f &&
            (tag == null || !preferTags) &&
            (tag == null || abs(tag - measured) >= 0.005f)
}
