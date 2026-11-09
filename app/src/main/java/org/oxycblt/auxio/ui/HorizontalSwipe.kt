/*
 * Copyright (c) 2026 Auxio Project
 * HorizontalSwipe.kt is part of Auxio.
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
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Tell whether a movement is a clear swipe to the side, so that scrolling up and down is not
 * mistaken for one.
 *
 * @param dx How far the finger moved to the right, negative to the left.
 * @param dy How far it moved down, negative up.
 * @param velocityX How fast it moved sideways at the end.
 * @param minDistance The least distance to the side that counts.
 * @param minVelocity The least speed to the side that counts.
 * @return True for a swipe to the left (forward), false for one to the right (back), null if it was
 *   not a clear swipe to the side.
 */
fun horizontalSwipe(
    dx: Float,
    dy: Float,
    velocityX: Float,
    minDistance: Float,
    minVelocity: Float,
): Boolean? {
    if (abs(dx) < minDistance || abs(dx) < 2 * abs(dy)) return null
    if (abs(velocityX) < minVelocity) return null
    return dx < 0
}

/**
 * Reports swipes to the left and right from the touches fed to it, without taking them from
 * whatever scrolls underneath.
 *
 * @param onSwipe Called with true for a swipe to the left, false for one to the right.
 */
class HorizontalSwipeDetector(context: Context, onSwipe: (forward: Boolean) -> Unit) {
    private val minDistance = 64 * context.resources.displayMetrics.density
    private val minVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val detector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    if (e1 == null) return false
                    val forward =
                        horizontalSwipe(
                            e2.x - e1.x,
                            e2.y - e1.y,
                            velocityX,
                            minDistance,
                            minVelocity.toFloat(),
                        ) ?: return false
                    onSwipe(forward)
                    return true
                }
            },
        )

    fun onTouchEvent(event: MotionEvent) {
        detector.onTouchEvent(event)
    }
}
