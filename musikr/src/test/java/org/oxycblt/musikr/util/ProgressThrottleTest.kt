/*
 * Copyright (c) 2026 Auxio Project
 * ProgressThrottleTest.kt is part of Auxio.
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
 
package org.oxycblt.musikr.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressThrottleTest {
    private var nowNs = 5_000_000_000L
    private val throttle = ProgressThrottle(100) { nowNs }

    private fun advanceMs(ms: Long) {
        nowNs += ms * 1_000_000L
    }

    @Test
    fun nothingIsReportedBeforeTheFirstInterval() {
        assertFalse(throttle.allow())
        advanceMs(99)
        assertFalse(throttle.allow())
    }

    @Test
    fun aReportGoesThroughOnceTheIntervalHasPassed() {
        advanceMs(100)
        assertTrue(throttle.allow())
    }

    @Test
    fun aReportThatWentThroughStartsTheIntervalAgain() {
        advanceMs(100)
        assertTrue(throttle.allow())
        advanceMs(50)
        assertFalse(throttle.allow())
        advanceMs(50)
        assertTrue(throttle.allow())
    }

    @Test
    fun manyCallsInOneIntervalLetOnlyOneThrough() {
        var allowed = 0
        repeat(10_000) {
            advanceMs(0)
            if (throttle.allow()) allowed++
        }
        assertTrue(allowed == 0)
        advanceMs(100)
        repeat(10_000) { if (throttle.allow()) allowed++ }
        assertTrue(allowed == 1)
    }

    @Test
    fun aLongPauseStillLetsOnlyOneThrough() {
        advanceMs(60_000)
        assertTrue(throttle.allow())
        assertFalse(throttle.allow())
    }
}
