/*
 * Copyright (c) 2026 Auxio Project
 * TagWriter.kt is part of Auxio.
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
 
package org.oxycblt.musikr.metadata

import android.os.ParcelFileDescriptor

/**
 * Noxio: writes tags into an audio file with TagLib. The fields are TagLib's format independent
 * property names, which TagLib turns into ID3v2 frames, MP4 atoms or Vorbis comments.
 */
object TagWriter {
    const val TITLE = "TITLE"
    const val ARTIST = "ARTIST"
    const val ALBUM = "ALBUM"
    const val ALBUM_ARTIST = "ALBUMARTIST"
    const val DATE = "DATE"
    const val TRACK = "TRACKNUMBER"
    const val DISC = "DISCNUMBER"
    const val TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN"
    const val TRACK_PEAK = "REPLAYGAIN_TRACK_PEAK"
    const val ALBUM_GAIN = "REPLAYGAIN_ALBUM_GAIN"
    const val ALBUM_PEAK = "REPLAYGAIN_ALBUM_PEAK"

    /**
     * Write [fields] into the file opened (for reading and writing) as [file], which is closed
     * afterwards. [extension] (like "mp3") tells the format, as an open file has no name. Every
     * field given replaces what the file has, an empty list removes it; fields not given are kept.
     * Blocking.
     *
     * @return Whether the file was saved.
     */
    fun write(
        file: ParcelFileDescriptor,
        extension: String,
        fields: Map<String, List<String>>,
    ): Boolean {
        if (fields.isEmpty()) {
            file.close()
            return true
        }
        val keys = fields.keys.toTypedArray()
        val values = keys.map { fields.getValue(it).toTypedArray() }.toTypedArray()
        // TagLib takes the descriptor over and closes it.
        return writeNative(file.detachFd(), extension.lowercase(), keys, values)
    }

    private external fun writeNative(
        fd: Int,
        extension: String,
        keys: Array<String>,
        values: Array<Array<String>>,
    ): Boolean

    init {
        System.loadLibrary("tagJNI")
    }
}
