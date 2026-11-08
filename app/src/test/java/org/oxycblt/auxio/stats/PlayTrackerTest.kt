/*
 * Copyright (c) 2026 Auxio Project
 * PlayTrackerTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayTrackerTest {
    private val tracker = PlayTracker()
    private val song = 200_000L // a 3:20 song

    private fun start(playing: Boolean = true, at: Long = 0, duration: Long = song) =
        tracker.begin("song", duration, wallNowMs = 1_000_000 + at, playing = playing, nowMs = at)

    @Test
    fun nothingIsTrackedAtFirst() {
        assertNull(tracker.current)
        assertNull(tracker.snapshot(10_000))
        assertEquals(0L, tracker.listened(10_000))
    }

    @Test
    fun timeCountsOnlyWhilePlaying() {
        start(at = 0)
        tracker.setPlaying(false, 10_000) // played 10 s
        tracker.setPlaying(true, 50_000) // paused for 40 s
        assertEquals(10_000L + 5_000L, tracker.listened(55_000))
    }

    @Test
    fun aSongStartedWhilePausedCountsNothingUntilItPlays() {
        start(playing = false)
        assertEquals(0L, tracker.listened(60_000))
        tracker.setPlaying(true, 60_000)
        assertEquals(20_000L, tracker.listened(80_000))
    }

    @Test
    fun playingTwiceInARowDoesNotCountTwice() {
        start()
        tracker.setPlaying(true, 5_000)
        tracker.setPlaying(true, 8_000)
        assertEquals(10_000L, tracker.listened(10_000))
    }

    @Test
    fun pausingTwiceInARowDoesNotCountTwice() {
        start()
        tracker.setPlaying(false, 10_000)
        tracker.setPlaying(false, 20_000)
        assertEquals(10_000L, tracker.listened(30_000))
    }

    @Test
    fun aPlayCountsAfterThirtySeconds() {
        start()
        assertNull(tracker.snapshot(29_999))
        val play = tracker.snapshot(30_000)!!
        assertEquals(30_000L, play.listenedMs)
        assertEquals(1_000_000L, play.startedAt)
    }

    @Test
    fun aShortSongCountsAfterHalfOfIt() {
        start(duration = 40_000) // 20 s is half
        assertNull(tracker.snapshot(19_999))
        assertEquals(20_000L, tracker.snapshot(20_000)!!.listenedMs)
    }

    @Test
    fun theThresholdIsThirtySecondsOrHalfTheSong() {
        assertEquals(30_000L, PlayTracker.threshold(200_000))
        assertEquals(30_000L, PlayTracker.threshold(60_000))
        assertEquals(20_000L, PlayTracker.threshold(40_000))
        assertEquals(30_000L, PlayTracker.threshold(0)) // unknown length
    }

    @Test
    fun beginningAnotherSongForgetsTheFirst() {
        start()
        tracker.begin("other", song, wallNowMs = 5, playing = true, nowMs = 100_000)
        assertEquals("other", tracker.current)
        assertEquals(0L, tracker.listened(100_000))
    }

    @Test
    fun clearingStopsTracking() {
        start()
        tracker.clear()
        assertNull(tracker.current)
        assertNull(tracker.snapshot(100_000))
        tracker.setPlaying(true, 100_000) // ignored without a song
        assertEquals(0L, tracker.listened(200_000))
    }

    // --- repeating a song ---

    @Test
    fun aSongThatStartsOverAfterBeingHeardIsALoop() {
        start()
        assertTrue(tracker.isLoop(positionMs = 500, nowMs = 195_000)) // heard 195 of 200 s
    }

    @Test
    fun seekingBackEarlyIsNotALoop() {
        start()
        assertFalse(tracker.isLoop(positionMs = 0, nowMs = 40_000)) // heard only 40 s
    }

    @Test
    fun aPositionInTheMiddleIsNotALoop() {
        start()
        assertFalse(tracker.isLoop(positionMs = 100_000, nowMs = 195_000))
    }

    @Test
    fun theStartOfASongIsNotALoop() {
        start()
        assertFalse(tracker.isLoop(positionMs = 0, nowMs = 0))
    }

    @Test
    fun withoutAKnownLengthThereIsNoLoop() {
        start(duration = 0)
        assertFalse(tracker.isLoop(positionMs = 0, nowMs = 500_000))
    }

    @Test
    fun repeatingASongOneHundredTimesGivesOneHundredPlays() {
        // The recorder starts a new play each time the tracker reports a loop.
        var plays = 0
        var clock = 0L
        start(at = clock)
        repeat(100) {
            clock += song
            if (tracker.isLoop(positionMs = 100, nowMs = clock)) {
                assertTrue(tracker.snapshot(clock) != null)
                plays++
                tracker.begin("song", song, wallNowMs = clock, playing = true, nowMs = clock)
            }
        }
        assertEquals(100, plays)
    }
}
