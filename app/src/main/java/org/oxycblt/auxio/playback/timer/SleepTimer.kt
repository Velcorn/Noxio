/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimer.kt is part of Auxio.
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
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.oxycblt.auxio.R

/** How a sleep timer decides when to stop playback. */
sealed interface SleepTimerMode {
    /** Stop after a fixed amount of [minutes]. */
    data class Minutes(val minutes: Int) : SleepTimerMode

    /** Stop once the currently playing track has finished. */
    data object EndOfTrack : SleepTimerMode
}

/** The current state of the [SleepTimer]. */
sealed interface SleepTimerState {
    data object Off : SleepTimerState

    /**
     * A running timer.
     *
     * @param mode How the timer ends.
     * @param endsAt When the timer ends, in [SystemClock.elapsedRealtime] milliseconds. Only
     *   meaningful for [SleepTimerMode.Minutes], 0 otherwise.
     * @param fade Whether volume should fade out before playback stops.
     */
    data class Active(val mode: SleepTimerMode, val endsAt: Long, val fade: Boolean) :
        SleepTimerState
}

/**
 * Holds the state of the sleep timer and the user's timer preferences. This is only the state: the
 * countdown and the actual stopping of playback is done by the playback service.
 */
interface SleepTimer {
    /** The current timer state. */
    val state: StateFlow<SleepTimerState>
    /** The mode that was started last, used as the default the next time. */
    val lastMode: SleepTimerMode
    /** The last minutes value entered as a custom duration, or null if never customized. */
    var lastCustomMinutes: Int?
    /** Whether to fade out before stopping. */
    var fade: Boolean
    /** Whether shaking the device restarts a running minutes timer. */
    val shakeToReset: Boolean

    /** Start a timer with the given [mode], replacing any running timer. */
    fun start(mode: SleepTimerMode)

    /** Restart a running minutes timer with its original duration. No-op otherwise. */
    fun reset()

    /** Stop the timer without affecting playback. */
    fun cancel()

    companion object {
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 12 * 60
        const val DEFAULT_CUSTOM_MINUTES = 45
    }
}

@Singleton
class SleepTimerImpl @Inject constructor(@ApplicationContext private val context: Context) :
    SleepTimer {
    private val sharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private val mutableState = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)

    override val state: StateFlow<SleepTimerState> = mutableState.asStateFlow()

    override val lastMode: SleepTimerMode
        get() {
            // 0 means end of track, which is also the default on first run.
            val minutes = sharedPreferences.getInt(KEY_LAST_MINUTES, 0)
            return if (minutes > 0) SleepTimerMode.Minutes(minutes) else SleepTimerMode.EndOfTrack
        }

    override var lastCustomMinutes: Int?
        get() =
            if (sharedPreferences.contains(KEY_CUSTOM_MINUTES)) {
                sharedPreferences.getInt(KEY_CUSTOM_MINUTES, 0)
            } else {
                null
            }
        set(value) =
            sharedPreferences.edit {
                if (value != null) putInt(KEY_CUSTOM_MINUTES, value) else remove(KEY_CUSTOM_MINUTES)
            }

    override var fade: Boolean
        get() = sharedPreferences.getBoolean(KEY_FADE, true)
        set(value) = sharedPreferences.edit { putBoolean(KEY_FADE, value) }

    override val shakeToReset: Boolean
        get() =
            sharedPreferences.getBoolean(context.getString(R.string.set_key_sleep_shake), true)

    override fun start(mode: SleepTimerMode) {
        val fixed =
            when (mode) {
                is SleepTimerMode.Minutes ->
                    SleepTimerMode.Minutes(
                        mode.minutes.coerceIn(SleepTimer.MIN_MINUTES, SleepTimer.MAX_MINUTES)
                    )
                SleepTimerMode.EndOfTrack -> mode
            }
        sharedPreferences.edit {
            putInt(KEY_LAST_MINUTES, (fixed as? SleepTimerMode.Minutes)?.minutes ?: 0)
        }
        val endsAt =
            if (fixed is SleepTimerMode.Minutes) {
                SystemClock.elapsedRealtime() + fixed.minutes * 60_000L
            } else {
                0L
            }
        mutableState.value = SleepTimerState.Active(fixed, endsAt, fade)
    }

    override fun reset() {
        val current = mutableState.value as? SleepTimerState.Active ?: return
        if (current.mode is SleepTimerMode.Minutes) {
            start(current.mode)
        }
    }

    override fun cancel() {
        mutableState.value = SleepTimerState.Off
    }

    private companion object {
        const val KEY_LAST_MINUTES = "noxio_sleep_last_minutes"
        const val KEY_CUSTOM_MINUTES = "noxio_sleep_custom_minutes"
        const val KEY_FADE = "noxio_sleep_fade"
    }
}
