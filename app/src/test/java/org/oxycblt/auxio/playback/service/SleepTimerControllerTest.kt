/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimerControllerTest.kt is part of Auxio.
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

import android.content.Context
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import java.lang.reflect.Proxy
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.timer.SleepTimer
import org.oxycblt.auxio.playback.timer.SleepTimerImpl
import org.oxycblt.auxio.playback.timer.SleepTimerMode
import org.oxycblt.auxio.playback.timer.SleepTimerState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensorManager
import org.robolectric.shadows.ShadowSystemClock

/**
 * What the timer does to the player while it counts down: the fade, the pause, letting the song
 * finish, and the shake. The player, the timer and the time are all fakes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SleepTimerControllerTest {
    private class FakeTimer : SleepTimer {
        private val mutable = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
        override val state: StateFlow<SleepTimerState> = mutable
        override val lastMode = SleepTimerMode.Minutes(30)
        override var lastCustomMinutes: Int? = null
        override var fade = true
        override var finishSong = true
        override var shakeToReset = true
        var resets = 0

        fun activate(mode: SleepTimerMode, endsAt: Long) {
            mutable.value = SleepTimerState.Active(mode, endsAt, fade, finishSong)
        }

        override fun start(mode: SleepTimerMode) =
            activate(mode, SystemClock.elapsedRealtime() + 60_000)

        override fun reset() {
            resets += 1
        }

        override fun finishTrack() {
            val active = mutable.value as SleepTimerState.Active
            mutable.value = active.copy(mode = SleepTimerMode.EndOfTrack)
        }

        override fun setPlaying(playing: Boolean) {}

        override fun extend(minutes: Int) {}

        override fun cancel() {
            mutable.value = SleepTimerState.Off
        }
    }

    private class FakePlayer {
        var volume = 1f
        var playing = true
        var durationMs = C.TIME_UNSET
        var positionMs = 0L

        val listeners = mutableListOf<Player.Listener>()

        val exo: ExoPlayer =
            Proxy.newProxyInstance(
                ExoPlayer::class.java.classLoader,
                arrayOf(ExoPlayer::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "addListener" -> {
                        listeners.add(args!![0] as Player.Listener)
                        Unit
                    }
                    "removeListener" -> {
                        listeners.remove(args!![0] as Player.Listener)
                        Unit
                    }
                    "getVolume" -> volume
                    "setVolume" -> volume = args!![0] as Float
                    "isPlaying" -> playing
                    "getDuration" -> durationMs
                    "getCurrentPosition" -> positionMs
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as ExoPlayer
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val timer = FakeTimer()
    private val player = FakePlayer()
    private val scope = TestScope()
    private val pausedAtEnd = mutableListOf<Boolean>()
    private var pausedPlayback = 0

    private val playbackManager: PlaybackStateManager =
        Proxy.newProxyInstance(
            PlaybackStateManager::class.java.classLoader,
            arrayOf(PlaybackStateManager::class.java),
        ) { _, method, args ->
            when (method.name) {
                "playing" -> if (args!![0] == false) pausedPlayback += 1
                else -> throw UnsupportedOperationException(method.name)
            }
        } as PlaybackStateManager

    private val controller =
        SleepTimerController(context, player.exo, playbackManager, timer, scope) {
            pausedAtEnd += it
        }

    init {
        controller.attach()
        scope.testScheduler.runCurrent()
    }

    private fun elapse(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(left, 100L)
            ShadowSystemClock.advanceBy(Duration.ofMillis(step))
            scope.testScheduler.advanceTimeBy(step)
            scope.testScheduler.runCurrent()
            left -= step
        }
    }

    private fun startMinutes(seconds: Long, fade: Boolean = true, finishSong: Boolean = false) {
        timer.fade = fade
        timer.finishSong = finishSong
        timer.activate(
            SleepTimerMode.Minutes((seconds / 60).toInt()),
            SystemClock.elapsedRealtime() + seconds * 1_000,
        )
        scope.testScheduler.runCurrent()
    }

    @Test
    fun theVolumeIsFullAndNothingIsPausedWhileTheTimerIsOff() {
        assertEquals(1f, player.volume, 0f)
        assertEquals(listOf<Boolean>(), pausedAtEnd.filter { it })
        assertEquals(0, pausedPlayback)
    }

    @Test
    fun theVolumeFadesOutInTheLastSecondsAndPlaybackPausesAtTheEnd() {
        startMinutes(60)
        elapse(40_000)
        assertEquals(1f, player.volume, 0f)
        // Four seconds of the eight second fade are left: a quarter of the loudness.
        elapse(16_000)
        assertEquals(0.25f, player.volume, 0.05f)
        elapse(5_000)
        assertEquals(1, pausedPlayback)
        // Cancelling brings the volume back for the next time.
        assertEquals(SleepTimerState.Off, timer.state.value)
        assertEquals(1f, player.volume, 0f)
    }

    @Test
    fun withoutAFadeTheVolumeStaysFull() {
        startMinutes(60, fade = false)
        elapse(55_000)
        assertEquals(1f, player.volume, 0f)
        elapse(6_000)
        assertEquals(1, pausedPlayback)
    }

    @Test
    fun aTimerThatLetsTheSongFinishWaitsForTheEndOfTheTrack() {
        startMinutes(60, finishSong = true)
        elapse(61_000)
        assertEquals(0, pausedPlayback)
        assertEquals(SleepTimerMode.EndOfTrack, (timer.state.value as SleepTimerState.Active).mode)
        assertTrue(pausedAtEnd.last())

        controller.onTrackEnded()
        scope.testScheduler.runCurrent()
        assertEquals(SleepTimerState.Off, timer.state.value)
        assertEquals(false, pausedAtEnd.last())
    }

    @Test
    fun actualPlaybackEventsFreezeTheTimerAndResumeItsFadeAndExpiry() {
        val realTimer = SleepTimerImpl(context)
        realTimer.finishSong = false
        realTimer.fade = true
        val realController =
            SleepTimerController(context, player.exo, playbackManager, realTimer, scope) {}
        realController.attach()
        realTimer.start(SleepTimerMode.Minutes(1))
        scope.testScheduler.runCurrent()
        elapse(56_000)
        assertEquals(0.25f, player.volume, 0.05f)
        player.playing = false
        player.listeners.toList().forEach { it.onIsPlayingChanged(false) }
        scope.testScheduler.runCurrent()
        assertEquals(1f, player.volume, 0f)
        elapse(120_000)
        assertEquals(0, pausedPlayback)
        assertEquals(4_000L, (realTimer.state.value as SleepTimerState.Active).remainingMs())
        player.playing = true
        player.listeners.toList().forEach { it.onIsPlayingChanged(true) }
        scope.testScheduler.runCurrent()
        elapse(5_000)
        assertEquals(1, pausedPlayback)
        assertEquals(SleepTimerState.Off, realTimer.state.value)
        realController.release()
        assertEquals(1, player.listeners.size) // Only the original controller remains.
    }

    @Test
    fun attachingToAPausedPlayerStartsTimersFrozen() {
        player.playing = false
        val realTimer = SleepTimerImpl(context)
        val realController =
            SleepTimerController(context, player.exo, playbackManager, realTimer, scope) {}
        realController.attach()
        realTimer.start(SleepTimerMode.Minutes(1))
        scope.testScheduler.runCurrent()
        elapse(120_000)
        assertEquals(60_000L, (realTimer.state.value as SleepTimerState.Active).remainingMs())
        assertEquals(0, pausedPlayback)
        realController.release()
    }

    @Test
    fun theEndOfTrackTimerFadesTheLastSecondsOfTheTrack() {
        player.durationMs = 100_000
        player.positionMs = 96_000
        timer.activate(SleepTimerMode.EndOfTrack, endsAt = 0)
        scope.testScheduler.runCurrent()
        elapse(200)
        assertEquals(0.25f, player.volume, 0.01f)

        // Paused or without a known length there is nothing to fade.
        player.playing = false
        elapse(200)
        assertEquals(1f, player.volume, 0f)
        player.playing = true
        player.durationMs = C.TIME_UNSET
        elapse(200)
        assertEquals(1f, player.volume, 0f)
    }

    @Test
    fun theEndOfTrackTimerDoesNotFadeWhenFadingIsOff() {
        timer.fade = false
        player.durationMs = 100_000
        player.positionMs = 99_000
        timer.activate(SleepTimerMode.EndOfTrack, endsAt = 0)
        scope.testScheduler.runCurrent()
        elapse(500)
        assertEquals(1f, player.volume, 0f)
    }

    @Test
    fun aTrackEndingDoesNothingToATimerThatIsNotWaitingForIt() {
        startMinutes(600)
        controller.onTrackEnded()
        assertTrue(timer.state.value is SleepTimerState.Active)
    }

    @Test
    fun cancellingDuringAFadeBringsTheMusicBack() {
        startMinutes(60)
        elapse(56_000)
        assertTrue(player.volume < 1f)
        timer.cancel()
        scope.testScheduler.runCurrent()
        assertEquals(1f, player.volume, 0f)
    }

    private fun shake(g: Float) {
        val event = ShadowSensorManager.createSensorEvent(3)
        // One axis is enough: the strength is the length of the whole vector.
        event.values[0] = g * SensorManager.GRAVITY_EARTH
        controller.onSensorChanged(event)
    }

    @Test
    fun threeStrongJoltsResetTheTimer() {
        startMinutes(600)
        repeat(3) {
            shake(3f)
            elapse(150)
        }
        assertEquals(1, timer.resets)
    }

    @Test
    fun gentleMovementDoesNotResetTheTimer() {
        startMinutes(600)
        repeat(10) {
            shake(1.2f)
            elapse(150)
        }
        assertEquals(0, timer.resets)
    }

    @Test
    fun releasingStopsTheTimerAndRestoresTheVolume() {
        startMinutes(600)
        player.volume = 0.3f
        controller.release()
        assertEquals(SleepTimerState.Off, timer.state.value)
        assertEquals(1f, player.volume, 0f)
        assertEquals(false, pausedAtEnd.last())
    }
}
