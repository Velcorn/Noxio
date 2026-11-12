/*
 * Copyright (c) 2026 Auxio Project
 * AutoSaverTest.kt is part of Auxio.
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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The position is saved every little while, but only while something plays. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoSaverTest {
    @Test
    fun savesAfterEveryIntervalWhilePlaying() = runTest {
        var saves = 0
        val saver = AutoSaver(backgroundScope, 1_000) { saves++ }
        saver.setPlaying(true)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(0, saves)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, saves)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(3, saves)
    }

    @Test
    fun stopsWhenPlaybackStopsAndStartsAgain() = runTest {
        var saves = 0
        val saver = AutoSaver(backgroundScope, 1_000) { saves++ }
        saver.setPlaying(true)
        assertTrue(saver.running)
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(1, saves)

        saver.setPlaying(false)
        assertFalse(saver.running)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, saves)

        saver.setPlaying(true)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, saves)
    }

    @Test
    fun playingTwiceDoesNotStartTwoTimers() = runTest {
        var saves = 0
        val saver = AutoSaver(backgroundScope, 1_000) { saves++ }
        saver.setPlaying(true)
        saver.setPlaying(true)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(3, saves)
    }

    @Test
    fun stoppingWhatNeverStartedIsFine() = runTest {
        val saver = AutoSaver(backgroundScope, 1_000) {}
        saver.setPlaying(false)
        assertFalse(saver.running)
    }
}
