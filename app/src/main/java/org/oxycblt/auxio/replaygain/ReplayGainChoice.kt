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
     * The adjustment in dB to use (of the track or of the album), or null if there is none. With
     * [preferTags] the [tag] comes first and the [measured] value fills in for songs without one;
     * otherwise it is the other way round.
     */
    fun choose(tag: Float?, measured: Float?, preferTags: Boolean): Float? =
        if (preferTags) tag ?: measured else measured ?: tag

    /**
     * Whether a scan has to measure a song. A song measured with everything the album adjustment
     * needs ([complete]) is done. Otherwise it is measured unless, with [preferTags], it has both a
     * track and an album tag (the tags would win anyway).
     */
    fun needsMeasuring(
        trackTag: Float?,
        albumTag: Float?,
        complete: Boolean,
        preferTags: Boolean,
    ): Boolean = !complete && (!preferTags || trackTag == null || albumTag == null)
}
