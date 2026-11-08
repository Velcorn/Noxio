/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimerMath.kt is part of Auxio.
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

/** The volume ramp of the sleep timer fade-out. */
object SleepFade {
    /**
     * Perceived loudness is roughly logarithmic, so a squared ramp sounds more even than a linear
     * one.
     *
     * @param fraction How much of the fade is left, 1 at the start and 0 at the end.
     * @return The volume from 0 to 1.
     */
    fun curve(fraction: Float) = fraction.coerceIn(0f, 1f).let { it * it }

    /**
     * The volume for a point in time.
     *
     * @param remainingMs How long until the timer ends.
     * @param fadeMs How long the fade lasts.
     * @return Full volume before the fade has started or when the end has already passed, the ramp
     *   in between.
     */
    fun volume(remainingMs: Long, fadeMs: Long): Float =
        if (fadeMs > 0 && remainingMs in 0..fadeMs) curve(remainingMs / fadeMs.toFloat()) else 1f
}

/**
 * Recognizes a shake of the device from accelerometer samples: [SPIKES] strong jolts, each at least
 * [MIN_SPIKE_GAP_MS] apart, inside [WINDOW_MS]. After a shake it ignores everything for
 * [COOLDOWN_MS], so one wave of the arm does not count twice.
 */
class ShakeDetector {
    private var count = 0
    private var windowStart = 0L
    private var lastSpike = NEVER
    private var lastShake = NEVER

    /** Forget the jolts so far, for example when listening starts again. */
    fun clear() {
        count = 0
    }

    /**
     * Feed one sample.
     *
     * @param g The strength of the acceleration, in multiples of gravity.
     * @param nowMs The time of the sample, in milliseconds on any steadily increasing clock.
     * @return True if this sample completed a shake.
     */
    fun onSample(g: Float, nowMs: Long): Boolean {
        if (g < THRESHOLD_G) {
            return false
        }
        if (nowMs - lastShake < COOLDOWN_MS || nowMs - lastSpike < MIN_SPIKE_GAP_MS) {
            return false
        }
        lastSpike = nowMs
        if (nowMs - windowStart > WINDOW_MS) {
            windowStart = nowMs
            count = 0
        }
        count++
        if (count >= SPIKES) {
            count = 0
            lastShake = nowMs
            return true
        }
        return false
    }

    companion object {
        const val THRESHOLD_G = 2.5f
        const val SPIKES = 3
        const val WINDOW_MS = 1_000L
        const val MIN_SPIKE_GAP_MS = 100L
        const val COOLDOWN_MS = 2_000L
        private const val NEVER = Long.MIN_VALUE / 2
    }
}
