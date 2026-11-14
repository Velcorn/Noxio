/*
 * Copyright (c) 2026 Auxio Project
 * SwipeUpTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeUpTest {
    private fun swipe(dx: Float, dy: Float, vx: Float, vy: Float) =
        SwipeUp.isSwipeUp(dx, dy, vx, vy, minDistance = 100f, minVelocity = 300f)

    @Test
    fun aQuickSwipeUpCounts() {
        assertTrue(swipe(dx = 10f, dy = -400f, vx = 50f, vy = -1500f))
    }

    @Test
    fun aSwipeDownDoesNot() {
        assertFalse(swipe(dx = 10f, dy = 400f, vx = 50f, vy = 1500f))
    }

    @Test
    fun aShortMoveDoesNot() {
        assertFalse(swipe(dx = 0f, dy = -60f, vx = 0f, vy = -1500f))
    }

    @Test
    fun aSlowMoveDoesNot() {
        assertFalse(swipe(dx = 0f, dy = -400f, vx = 0f, vy = -100f))
    }

    @Test
    fun aSidewaysSwipeDoesNot() {
        assertFalse(swipe(dx = 500f, dy = -150f, vx = 2000f, vy = -500f))
    }

    @Test
    fun aDiagonalSwipeThatIsMoreSidewaysThanUpDoesNot() {
        assertFalse(swipe(dx = 300f, dy = -400f, vx = 900f, vy = -1000f))
    }

    @Test
    fun theLimitsThemselvesCount() {
        assertTrue(swipe(dx = 0f, dy = -100f, vx = 0f, vy = -300f))
    }
}
