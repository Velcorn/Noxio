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
     * @param finishSong Whether the song that is playing when the time is up is played to its end
     *   before playback stops, instead of stopping right away.
     */
    data class Active(
        val mode: SleepTimerMode,
        val endsAt: Long,
        val fade: Boolean,
        val finishSong: Boolean = false,
    ) : SleepTimerState
}

/**
 * Holds the state of the sleep timer and the user's timer preferences. This is only the state: the
 * countdown and the actual stopping of playback is done by the playback service.
 */
interface SleepTimer {
    /** The current timer state. */
    val state: StateFlow<SleepTimerState>
    /** The mode that was started last, used as the default the next time. */
    val lastMode: SleepTimerMode.Minutes
    /** The last minutes value entered as a custom duration, or null if never customized. */
    var lastCustomMinutes: Int?
    /** Whether to fade out before stopping. */
    var fade: Boolean
    /** Whether the song that is playing when the time is up is played to its end first. */
    var finishSong: Boolean
    /** Whether shaking the device restarts a running minutes timer. */
    var shakeToReset: Boolean

    /** Start a timer with the given [mode], replacing any running timer. */
    fun start(mode: SleepTimerMode)

    /** Restart a running minutes timer with its original duration. No-op otherwise. */
    fun reset()

    /**
     * The time of a minutes timer is up and the song is allowed to finish: wait for the end of the
     * track instead. The remembered duration is not changed. No-op without a minutes timer.
     */
    fun finishTrack()

    /**
     * Give a running minutes timer [minutes] more, for example from the notification. The
     * remembered duration that a restart goes back to is not changed. No-op otherwise.
     */
    fun extend(minutes: Int)

    /** Stop the timer without affecting playback. */
    fun cancel()

    companion object {
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 12 * 60
        const val DEFAULT_CUSTOM_MINUTES = 45
        const val DEFAULT_MINUTES = 30
        /** How much the notification's button adds to a running timer. */
        const val EXTEND_MINUTES = 10
    }
}

@Singleton
class SleepTimerImpl @Inject constructor(@ApplicationContext private val context: Context) :
    SleepTimer {
    private val sharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private val mutableState = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)

    override val state: StateFlow<SleepTimerState> = mutableState.asStateFlow()

    override val lastMode: SleepTimerMode.Minutes
        get() {
            // Older versions stored 0 for an end of track timer, which no longer exists.
            val minutes = sharedPreferences.getInt(KEY_LAST_MINUTES, 0)
            return SleepTimerMode.Minutes(if (minutes > 0) minutes else SleepTimer.DEFAULT_MINUTES)
        }

    override var lastCustomMinutes: Int?
        get() =
            if (sharedPreferences.contains(KEY_CUSTOM_MINUTES)) {
                sharedPreferences.getInt(KEY_CUSTOM_MINUTES, 0)
            } else {
                null
            }
        set(value) = sharedPreferences.edit {
            if (value != null) putInt(KEY_CUSTOM_MINUTES, value) else remove(KEY_CUSTOM_MINUTES)
        }

    override var fade: Boolean
        get() = sharedPreferences.getBoolean(KEY_FADE, true)
        set(value) = sharedPreferences.edit { putBoolean(KEY_FADE, value) }

    override var finishSong: Boolean
        get() = sharedPreferences.getBoolean(KEY_FINISH_SONG, true)
        set(value) = sharedPreferences.edit { putBoolean(KEY_FINISH_SONG, value) }

    override var shakeToReset: Boolean
        get() = sharedPreferences.getBoolean(context.getString(R.string.set_key_sleep_shake), true)
        set(value) = sharedPreferences.edit {
            putBoolean(context.getString(R.string.set_key_sleep_shake), value)
        }

    override fun start(mode: SleepTimerMode) {
        val fixed =
            when (mode) {
                is SleepTimerMode.Minutes ->
                    SleepTimerMode.Minutes(
                        mode.minutes.coerceIn(SleepTimer.MIN_MINUTES, SleepTimer.MAX_MINUTES)
                    )
                SleepTimerMode.EndOfTrack -> mode
            }
        if (fixed is SleepTimerMode.Minutes) {
            sharedPreferences.edit { putInt(KEY_LAST_MINUTES, fixed.minutes) }
        }
        val endsAt =
            if (fixed is SleepTimerMode.Minutes) {
                SystemClock.elapsedRealtime() + fixed.minutes * 60_000L
            } else {
                0L
            }
        mutableState.value = SleepTimerState.Active(fixed, endsAt, fade, finishSong)
    }

    override fun reset() {
        val current = mutableState.value as? SleepTimerState.Active ?: return
        if (current.mode is SleepTimerMode.Minutes) {
            start(current.mode)
        }
    }

    override fun finishTrack() {
        val current = mutableState.value as? SleepTimerState.Active ?: return
        if (current.mode is SleepTimerMode.Minutes) {
            mutableState.value = SleepTimerState.Active(SleepTimerMode.EndOfTrack, 0L, current.fade)
        }
    }

    override fun extend(minutes: Int) {
        val current = mutableState.value as? SleepTimerState.Active ?: return
        if (current.mode is SleepTimerMode.Minutes) {
            // Never more than the longest timer from now.
            val latest = SystemClock.elapsedRealtime() + SleepTimer.MAX_MINUTES * 60_000L
            mutableState.value =
                current.copy(endsAt = minOf(current.endsAt + minutes * 60_000L, latest))
        }
    }

    override fun cancel() {
        mutableState.value = SleepTimerState.Off
    }

    private companion object {
        const val KEY_LAST_MINUTES = "noxio_sleep_last_minutes"
        const val KEY_CUSTOM_MINUTES = "noxio_sleep_custom_minutes"
        const val KEY_FADE = "noxio_sleep_fade"
        const val KEY_FINISH_SONG = "noxio_sleep_finish_song"
    }
}
