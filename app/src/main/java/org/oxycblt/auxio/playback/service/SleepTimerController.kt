/*
 * Copyright (c) 2026 Noxio Project
 * SleepTimerController.kt is part of Noxio.
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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.timer.SleepTimer
import org.oxycblt.auxio.playback.timer.SleepTimerMode
import org.oxycblt.auxio.playback.timer.SleepTimerState
import timber.log.Timber as L

/**
 * Runs the [SleepTimer] inside the playback service: counts down, fades the volume, stops playback
 * and listens for shakes. Lives next to the player since it needs to control its volume and
 * pause-at-end behavior.
 *
 * @param onPauseAtEndChanged Called when the timer needs the player to pause at the end of the
 *   current track (or no longer needs that).
 */
@OptIn(UnstableApi::class)
class SleepTimerController(
    private val context: Context,
    private val player: ExoPlayer,
    private val playbackManager: PlaybackStateManager,
    private val sleepTimer: SleepTimer,
    private val onPauseAtEndChanged: (Boolean) -> Unit,
) : SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private var listeningForShake = false
    private var shakeCount = 0
    private var shakeWindowStart = 0L
    private var lastSpike = 0L
    private var lastReset = 0L

    fun attach() {
        scope.launch { sleepTimer.state.collect(::onState) }
    }

    fun release() {
        scope.cancel()
        stopListeningForShake()
        // The service is going away, don't leave a timer armed for the next one.
        sleepTimer.cancel()
        onPauseAtEndChanged(false)
        player.volume = 1f
    }

    /** Called by the player holder when playback paused because a track finished. */
    fun onTrackEnded() {
        val state = sleepTimer.state.value
        if (state is SleepTimerState.Active && state.mode == SleepTimerMode.EndOfTrack) {
            L.d("Track ended, sleep timer done")
            sleepTimer.cancel()
        }
    }

    private fun onState(state: SleepTimerState) {
        job?.cancel()
        // Any state change (cancel, reset, new timer) puts the volume back, so a reset during a
        // fade-out brings the music back.
        player.volume = 1f
        when (state) {
            SleepTimerState.Off -> {
                onPauseAtEndChanged(false)
                stopListeningForShake()
            }
            is SleepTimerState.Active ->
                when (state.mode) {
                    is SleepTimerMode.Minutes -> {
                        onPauseAtEndChanged(false)
                        startListeningForShake()
                        job = scope.launch { countdown(state) }
                    }
                    SleepTimerMode.EndOfTrack -> {
                        onPauseAtEndChanged(true)
                        stopListeningForShake()
                        job = scope.launch { fadeBeforeTrackEnd(state) }
                    }
                }
        }
    }

    private suspend fun countdown(state: SleepTimerState.Active) {
        val fadeMs = if (state.fade) FADE_MS else 0L
        delay((state.endsAt - fadeMs - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        while (true) {
            val remaining = state.endsAt - SystemClock.elapsedRealtime()
            if (remaining <= 0) {
                break
            }
            if (fadeMs > 0) {
                player.volume = fadeCurve(remaining / fadeMs.toFloat())
            }
            delay(STEP_MS)
        }
        L.d("Sleep timer expired, pausing")
        playbackManager.playing(false)
        // Cancelling restores the volume for the next time playback is started.
        sleepTimer.cancel()
    }

    private suspend fun fadeBeforeTrackEnd(state: SleepTimerState.Active) {
        // Pausing at the end of the track is done by the player (see onPauseAtEndChanged), all
        // we do here is the fade. Recomputing from the player every step means seeking and
        // skipping around the end of a track behave sensibly.
        while (state.fade) {
            val duration = player.duration
            val volume =
                if (player.isPlaying && duration != C.TIME_UNSET) {
                    val remaining = duration - player.currentPosition
                    if (remaining in 0..FADE_MS) fadeCurve(remaining / FADE_MS.toFloat()) else 1f
                } else {
                    1f
                }
            player.volume = volume
            delay(STEP_MS)
        }
    }

    // Perceived loudness is roughly logarithmic, so a squared ramp sounds more even than a
    // linear one.
    private fun fadeCurve(fraction: Float) = fraction.coerceIn(0f, 1f).let { it * it }

    // --- SHAKE TO RESET ---

    private fun startListeningForShake() {
        if (listeningForShake || !sleepTimer.shakeToReset) {
            return
        }
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        shakeCount = 0
        listeningForShake =
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    private fun stopListeningForShake() {
        if (listeningForShake) {
            sensorManager?.unregisterListener(this)
            listeningForShake = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (g < SHAKE_THRESHOLD_G) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastReset < SHAKE_COOLDOWN_MS || now - lastSpike < SHAKE_MIN_SPIKE_GAP_MS) {
            return
        }
        lastSpike = now
        if (now - shakeWindowStart > SHAKE_WINDOW_MS) {
            shakeWindowStart = now
            shakeCount = 0
        }
        shakeCount++
        if (shakeCount >= SHAKE_SPIKES) {
            L.d("Shake detected, resetting sleep timer")
            shakeCount = 0
            lastReset = now
            sleepTimer.reset()
            vibrate()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun vibrate() {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(VIBRATE_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(VIBRATE_MS)
        }
    }

    private companion object {
        const val FADE_MS = 8_000L
        const val STEP_MS = 100L
        const val SHAKE_THRESHOLD_G = 2.5f
        const val SHAKE_SPIKES = 3
        const val SHAKE_WINDOW_MS = 1_000L
        const val SHAKE_MIN_SPIKE_GAP_MS = 100L
        const val SHAKE_COOLDOWN_MS = 2_000L
        const val VIBRATE_MS = 80L
    }
}
