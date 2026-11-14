/*
 * Copyright (c) 2026 Auxio Project
 * ReplayGainChoice.kt is part of Auxio.
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
 
package org.oxycblt.auxio.replaygain

/** Which ReplayGain value a song uses: its tag or the loudness Noxio measured. */
object ReplayGainChoice {
    /**
     * The track adjustment in dB to use, or null if there is none. With [preferTags] the [tag]
     * comes first and the [measured] value fills in for songs without one; otherwise it is the
     * other way round.
     */
    fun trackGain(tag: Float?, measured: Float?, preferTags: Boolean): Float? =
        if (preferTags) tag ?: measured else measured ?: tag

    /**
     * Whether a scan has to measure a song: it has no measured value yet and, when [preferTags] is
     * set, no tag either (a tag would win anyway).
     */
    fun needsMeasuring(tag: Float?, measured: Float?, preferTags: Boolean): Boolean =
        measured == null && (!preferTags || tag == null)
}
