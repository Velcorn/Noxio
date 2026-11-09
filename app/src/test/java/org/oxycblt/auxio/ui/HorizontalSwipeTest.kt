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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
