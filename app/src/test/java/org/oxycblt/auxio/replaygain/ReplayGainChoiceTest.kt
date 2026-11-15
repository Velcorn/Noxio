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
    fun choose_tagsPreferred_tagWins() {
        assertEquals(-3f, ReplayGainChoice.choose(-3f, -7f, preferTags = true))
    }

    @Test
    fun choose_tagsPreferred_measuredFillsIn() {
        assertEquals(-7f, ReplayGainChoice.choose(null, -7f, preferTags = true))
    }

    @Test
    fun choose_measuredPreferred_measuredWins() {
        assertEquals(-7f, ReplayGainChoice.choose(-3f, -7f, preferTags = false))
    }

    @Test
    fun choose_measuredPreferred_tagFillsIn() {
        assertEquals(-3f, ReplayGainChoice.choose(-3f, null, preferTags = false))
    }

    @Test
    fun choose_nothing_isNull() {
        assertNull(ReplayGainChoice.choose(null, null, preferTags = true))
        assertNull(ReplayGainChoice.choose(null, null, preferTags = false))
    }

    @Test
    fun needsMeasuring_tagsPreferred_onlySongsMissingATag() {
        assertTrue(ReplayGainChoice.needsMeasuring(null, null, false, preferTags = true))
        // The album adjustment needs every song of the album, also ones with a track tag.
        assertTrue(ReplayGainChoice.needsMeasuring(-3f, null, false, preferTags = true))
        assertTrue(ReplayGainChoice.needsMeasuring(null, -4f, false, preferTags = true))
        assertFalse(ReplayGainChoice.needsMeasuring(-3f, -4f, false, preferTags = true))
    }

    @Test
    fun needsMeasuring_measuredPreferred_taggedSongsToo() {
        assertTrue(ReplayGainChoice.needsMeasuring(-3f, -4f, false, preferTags = false))
        assertTrue(ReplayGainChoice.needsMeasuring(null, null, false, preferTags = false))
    }

    @Test
    fun needsMeasuring_completelyMeasured_isSkipped() {
        assertFalse(ReplayGainChoice.needsMeasuring(null, null, true, preferTags = true))
        assertFalse(ReplayGainChoice.needsMeasuring(-3f, -4f, true, preferTags = false))
    }
}
