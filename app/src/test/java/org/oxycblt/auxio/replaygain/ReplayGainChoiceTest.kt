/*
 * Copyright (c) 2026 Auxio Project
 * ReplayGainChoiceTest.kt is part of Auxio.
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayGainChoiceTest {
    @Test
    fun trackGain_tagsPreferred_tagWins() {
        assertEquals(-3f, ReplayGainChoice.trackGain(-3f, -7f, preferTags = true))
    }

    @Test
    fun trackGain_tagsPreferred_measuredFillsIn() {
        assertEquals(-7f, ReplayGainChoice.trackGain(null, -7f, preferTags = true))
    }

    @Test
    fun trackGain_measuredPreferred_measuredWins() {
        assertEquals(-7f, ReplayGainChoice.trackGain(-3f, -7f, preferTags = false))
    }

    @Test
    fun trackGain_measuredPreferred_tagFillsIn() {
        assertEquals(-3f, ReplayGainChoice.trackGain(-3f, null, preferTags = false))
    }

    @Test
    fun trackGain_nothing_isNull() {
        assertNull(ReplayGainChoice.trackGain(null, null, preferTags = true))
        assertNull(ReplayGainChoice.trackGain(null, null, preferTags = false))
    }

    @Test
    fun needsMeasuring_tagsPreferred_onlyUntaggedSongs() {
        assertTrue(ReplayGainChoice.needsMeasuring(null, null, preferTags = true))
        assertFalse(ReplayGainChoice.needsMeasuring(-3f, null, preferTags = true))
    }

    @Test
    fun needsMeasuring_measuredPreferred_taggedSongsToo() {
        assertTrue(ReplayGainChoice.needsMeasuring(-3f, null, preferTags = false))
        assertTrue(ReplayGainChoice.needsMeasuring(null, null, preferTags = false))
    }

    @Test
    fun needsMeasuring_alreadyMeasured_isSkipped() {
        assertFalse(ReplayGainChoice.needsMeasuring(null, -7f, preferTags = true))
        assertFalse(ReplayGainChoice.needsMeasuring(-3f, -7f, preferTags = false))
    }
}
