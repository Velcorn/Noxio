/*
 * Copyright (c) 2026 Auxio Project
 * HorizontalSwipeTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.ui

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class HorizontalSwipeTest {
    private fun swipe(dx: Float, dy: Float = 0f, velocityX: Float = 2000f) =
        horizontalSwipe(dx, dy, velocityX, minDistance = 100f, minVelocity = 500f)

    @Test fun aSwipeToTheLeftGoesForward() = assertEquals(true, swipe(-300f))

    @Test fun aSwipeToTheRightGoesBack() = assertEquals(false, swipe(300f))

    @Test fun aShortMovementIsNotASwipe() = assertNull(swipe(60f))

    @Test fun aSlowMovementIsNotASwipe() = assertNull(swipe(300f, velocityX = 100f))

    @Test
    fun scrollingUpAndDownIsNotASwipe() {
        // More up than to the side.
        assertNull(swipe(150f, dy = 400f))
        // Sideways but not clearly: it has to be at least twice as far as it is up or down.
        assertNull(swipe(150f, dy = 100f))
    }

    @Test fun aSlightlyCrookedSwipeStillCounts() = assertEquals(false, swipe(300f, dy = 120f))
}

/** The detector on real touch events. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HorizontalSwipeDetectorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val swipes = mutableListOf<Boolean>()
    private val detector = HorizontalSwipeDetector(context) { swipes += it }

    /** A finger that moves from ([fromX], 1000) to ([toX], 1000 + [dy]) in 40 milliseconds. */
    private fun drag(fromX: Float, toX: Float, dy: Float = 0f) {
        val start = SystemClock.uptimeMillis()
        val steps = 8
        for (i in 0..steps) {
            val action =
                when (i) {
                    0 -> MotionEvent.ACTION_DOWN
                    steps -> MotionEvent.ACTION_UP
                    else -> MotionEvent.ACTION_MOVE
                }
            val fraction = i / steps.toFloat()
            val event =
                MotionEvent.obtain(
                    start,
                    start + i * 5L,
                    action,
                    fromX + (toX - fromX) * fraction,
                    1000f + dy * fraction,
                    0,
                )
            detector.onTouchEvent(event)
            event.recycle()
        }
    }

    @Test
    fun aFastDragToTheLeftIsAForwardSwipe() {
        drag(900f, 200f)
        assertEquals(listOf(true), swipes)
    }

    @Test
    fun aFastDragToTheRightIsABackSwipe() {
        drag(200f, 900f)
        assertEquals(listOf(false), swipes)
    }

    @Test
    fun aDragUpAndDownIsNotASwipe() {
        drag(500f, 540f, dy = 800f)
        assertEquals(emptyList<Boolean>(), swipes)
    }

    @Test
    fun aShortDragIsNotASwipe() {
        drag(500f, 470f)
        assertEquals(emptyList<Boolean>(), swipes)
    }
}
