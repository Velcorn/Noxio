/*
 * Copyright (c) 2026 Auxio Project
 * SwipeUp.kt is part of Auxio.
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

import kotlin.math.abs

/**
 * Tells a quick swipe up from other touches. Used to open the queue from the open player, where
 * swiping down already closes it.
 */
object SwipeUp {
    /**
     * @param dx The horizontal distance from where the finger went down to where it was lifted.
     * @param dy The vertical distance, negative when the finger moved up.
     * @param velocityX The horizontal speed at the end, in pixels per second.
     * @param velocityY The vertical speed at the end, negative when the finger moved up.
     * @param minDistance The shortest distance, in pixels, that counts as a swipe.
     * @param minVelocity The slowest speed, in pixels per second, that counts as a swipe.
     * @return Whether this was a clear swipe up: far enough, fast enough and more up than sideways,
     *   so that a sideways swipe on the cover or a drag on the seek bar is not taken for one.
     */
    fun isSwipeUp(
        dx: Float,
        dy: Float,
        velocityX: Float,
        velocityY: Float,
        minDistance: Float,
        minVelocity: Float,
    ) =
        dy <= -minDistance &&
            velocityY <= -minVelocity &&
            abs(dy) > 2 * abs(dx) &&
            abs(velocityY) > 2 * abs(velocityX)
}
