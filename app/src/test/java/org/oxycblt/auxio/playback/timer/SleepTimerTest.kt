/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimerTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.timer

import android.content.Context
import android.os.SystemClock
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SleepTimerTest {
    private lateinit var timer: SleepTimerImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        timer = SleepTimerImpl(context)
    }

    private fun active() = timer.state.value as SleepTimerState.Active

    @Test
    fun itStartsOff() {
        assertEquals(SleepTimerState.Off, timer.state.value)
    }

    @Test
    fun aMinutesTimerEndsThatManyMinutesFromNow() {
        val before = SystemClock.elapsedRealtime()
        timer.start(SleepTimerMode.Minutes(30))

        assertEquals(SleepTimerMode.Minutes(30), active().mode)
        assertEquals(before + 30 * 60_000L, active().endsAt)
    }

    @Test
    fun anEndOfTrackTimerHasNoEndTime() {
        timer.start(SleepTimerMode.EndOfTrack)
        assertEquals(SleepTimerMode.EndOfTrack, active().mode)
        assertEquals(0L, active().endsAt)
    }

    @Test
    fun minutesAreKeptWithinTheAllowedRange() {
        timer.start(SleepTimerMode.Minutes(0))
        assertEquals(SleepTimerMode.Minutes(SleepTimer.MIN_MINUTES), active().mode)

        timer.start(SleepTimerMode.Minutes(100_000))
        assertEquals(SleepTimerMode.Minutes(SleepTimer.MAX_MINUTES), active().mode)
    }

    @Test
    fun theLastDurationIsRememberedForNextTime() {
        assertEquals(
            SleepTimerMode.Minutes(SleepTimer.DEFAULT_MINUTES),
            timer.lastMode,
        ) // first run

        timer.start(SleepTimerMode.Minutes(25))
        assertEquals(SleepTimerMode.Minutes(25), timer.lastMode)
    }

    @Test
    fun waitingForTheEndOfTheSongDoesNotChangeTheRememberedDuration() {
        timer.start(SleepTimerMode.Minutes(25))
        timer.finishTrack()
        assertEquals(SleepTimerMode.Minutes(25), timer.lastMode)
    }

    @Test
    fun finishingTheLastSongIsOnByDefaultAndCopiedIntoTheTimer() {
        assertTrue(timer.finishSong)
        timer.start(SleepTimerMode.Minutes(5))
        assertTrue(active().finishSong)

        timer.finishSong = false
        timer.start(SleepTimerMode.Minutes(5))
        assertFalse(active().finishSong)
    }

    @Test
    fun whenTheTimeIsUpTheTimerWaitsForTheEndOfTheSongAndKeepsTheFade() {
        timer.fade = false
        timer.start(SleepTimerMode.Minutes(5))
        timer.finishTrack()

        assertEquals(SleepTimerMode.EndOfTrack, active().mode)
        assertEquals(0L, active().endsAt)
        assertFalse(active().fade)
        // This is the last phase: it does not wait for another song after that.
        assertFalse(active().finishSong)
    }

    @Test
    fun finishingTheTrackDoesNothingWithoutAMinutesTimer() {
        timer.finishTrack()
        assertEquals(SleepTimerState.Off, timer.state.value)

        timer.start(SleepTimerMode.EndOfTrack)
        timer.finishTrack()
        assertEquals(SleepTimerMode.EndOfTrack, active().mode)
    }

    @Test
    fun resettingRestartsTheFullDuration() {
        timer.start(SleepTimerMode.Minutes(10))
        ShadowSystemClock.advanceBy(Duration.ofMinutes(6))
        timer.reset()

        assertEquals(SystemClock.elapsedRealtime() + 10 * 60_000L, active().endsAt)
    }

    @Test
    fun resettingDoesNothingWithoutAMinutesTimer() {
        timer.reset()
        assertEquals(SleepTimerState.Off, timer.state.value)

        timer.start(SleepTimerMode.EndOfTrack)
        timer.reset()
        assertEquals(SleepTimerMode.EndOfTrack, active().mode)
    }

    @Test
    fun cancellingTurnsTheTimerOff() {
        timer.start(SleepTimerMode.Minutes(5))
        timer.cancel()
        assertEquals(SleepTimerState.Off, timer.state.value)
    }

    @Test
    fun fadeIsOnByDefaultAndCopiedIntoTheTimer() {
        assertTrue(timer.fade)
        timer.start(SleepTimerMode.Minutes(5))
        assertTrue(active().fade)

        timer.fade = false
        timer.start(SleepTimerMode.Minutes(5))
        assertFalse(active().fade)
    }

    @Test
    fun theCustomMinutesCanBeSetAndCleared() {
        assertNull(timer.lastCustomMinutes)
        timer.lastCustomMinutes = 42
        assertEquals(42, timer.lastCustomMinutes)
        timer.lastCustomMinutes = null
        assertNull(timer.lastCustomMinutes)
    }
}
