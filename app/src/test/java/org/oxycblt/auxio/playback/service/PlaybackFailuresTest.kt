/*
 * Copyright (c) 2026 Auxio Project
 * PlaybackFailuresTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFailuresTest {
    @Test
    fun stopsAfterEveryQueueEntryHasFailedOnce() {
        val failures = PlaybackFailures()
        repeat(4) { assertFalse(failures.shouldStop(5)) }
        assertTrue(failures.shouldStop(5))
        assertTrue(failures.shouldStop(5))
    }

    @Test
    fun aSingleSongOrEmptyQueueStopsImmediately() {
        assertTrue(PlaybackFailures().shouldStop(1))
        assertTrue(PlaybackFailures().shouldStop(0))
    }

    @Test
    fun successfullyCompletingASongAllowsAnotherFullPass() {
        val failures = PlaybackFailures()
        assertFalse(failures.shouldStop(2))
        failures.reset()
        assertFalse(failures.shouldStop(2))
        assertTrue(failures.shouldStop(2))
    }

    @Test
    fun shrinkingTheQueueStillBoundsRecovery() {
        val failures = PlaybackFailures()
        assertFalse(failures.shouldStop(4))
        assertTrue(failures.shouldStop(2))
    }
}
