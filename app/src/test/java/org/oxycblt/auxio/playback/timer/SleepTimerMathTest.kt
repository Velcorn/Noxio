/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimerMathTest.kt is part of Auxio.
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepFadeTest {
    @Test
    fun theCurveGoesFromFullVolumeToSilence() {
        assertEquals(1f, SleepFade.curve(1f), 0.0001f)
        assertEquals(0f, SleepFade.curve(0f), 0.0001f)
    }

    @Test
    fun theCurveIsSquaredSoTheMiddleIsQuieterThanLinear() {
        assertEquals(0.25f, SleepFade.curve(0.5f), 0.0001f)
    }

    @Test
    fun theCurveNeverLeavesZeroToOne() {
        assertEquals(1f, SleepFade.curve(3f), 0.0001f)
        assertEquals(0f, SleepFade.curve(-3f), 0.0001f)
    }

    @Test
    fun theVolumeIsFullBeforeTheFadeStarts() {
        assertEquals(1f, SleepFade.volume(remainingMs = 20_000, fadeMs = 8_000), 0.0001f)
    }

    @Test
    fun theVolumeFallsDuringTheFadeAndEndsSilent() {
        val early = SleepFade.volume(remainingMs = 6_000, fadeMs = 8_000)
        val late = SleepFade.volume(remainingMs = 2_000, fadeMs = 8_000)
        assertTrue(early > late)
        assertEquals(0f, SleepFade.volume(remainingMs = 0, fadeMs = 8_000), 0.0001f)
    }

    @Test
    fun aTimeInThePastKeepsFullVolumeLikeSeekingBackFromTheEnd() {
        assertEquals(1f, SleepFade.volume(remainingMs = -500, fadeMs = 8_000), 0.0001f)
    }

    @Test
    fun noFadeMeansFullVolume() {
        assertEquals(1f, SleepFade.volume(remainingMs = 100, fadeMs = 0), 0.0001f)
    }
}

class ShakeDetectorTest {
    private val strong = ShakeDetector.THRESHOLD_G + 1f
    private val start = 10_000L

    /** Feeds strong jolts at the given times and returns when a shake was reported. */
    private fun shakes(detector: ShakeDetector, vararg times: Long) = times.filter {
        detector.onSample(strong, it)
    }

    @Test
    fun threeStrongJoltsInAQuickSuccessionAreAShake() {
        val detector = ShakeDetector()
        assertEquals(listOf(start + 400), shakes(detector, start, start + 200, start + 400))
    }

    @Test
    fun weakMovementIsIgnored() {
        val detector = ShakeDetector()
        repeat(50) { assertFalse(detector.onSample(1.2f, start + it * 150L)) }
    }

    @Test
    fun twoJoltsAreNotEnough() {
        assertEquals(emptyList<Long>(), shakes(ShakeDetector(), start, start + 200))
    }

    @Test
    fun joltsThatAreTooSlowDoNotAddUp() {
        // Each jolt is more than a second after the previous one, so the count starts over.
        assertEquals(
            emptyList<Long>(),
            shakes(ShakeDetector(), start, start + 1_500, start + 3_000, start + 4_500),
        )
    }

    @Test
    fun samplesInsideTheSameJoltCountOnce() {
        // The accelerometer reports many samples per jolt, which must not look like a shake.
        val detector = ShakeDetector()
        val times = (0..9).map { start + it * 10L } // one jolt of 90 ms
        assertEquals(emptyList<Long>(), shakes(detector, *times.toLongArray()))
    }

    @Test
    fun aShakeIsNotCountedTwiceInARow() {
        val detector = ShakeDetector()
        val first = shakes(detector, start, start + 200, start + 400)
        // More jolts right after, inside the cooldown.
        val second = shakes(detector, start + 600, start + 800, start + 1_000)
        assertEquals(1, first.size)
        assertEquals(emptyList<Long>(), second)
    }

    @Test
    fun anotherShakeCountsAfterTheCooldown() {
        val detector = ShakeDetector()
        shakes(detector, start, start + 200, start + 400)
        val later = start + 400 + ShakeDetector.COOLDOWN_MS
        assertEquals(
            listOf(later + 400),
            shakes(detector, later, later + 200, later + 400),
        )
    }

    @Test
    fun clearingForgetsTheJoltsSoFar() {
        val detector = ShakeDetector()
        shakes(detector, start, start + 200)
        detector.clear()
        // The third jolt now counts as the first one.
        assertEquals(emptyList<Long>(), shakes(detector, start + 400))
    }
}
