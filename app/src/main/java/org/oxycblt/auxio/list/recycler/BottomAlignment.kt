/*
 * Copyright (c) 2026 Auxio Project
 * BottomAlignment.kt is part of Auxio.
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
 
package org.oxycblt.auxio.list.recycler

/**
 * How a list that is shorter than its view is placed: pushed down so that it sits at the bottom,
 * and whether the space kept at the bottom for a floating button is still needed.
 *
 * @param gap How far to push the first item down. 0 if the list fills the view or is longer.
 * @param keepBottomSpace Whether the space for the floating button is kept. A short list goes all
 *   the way to the bottom without it, a list that has to scroll needs it so that the last item can
 *   be scrolled clear of the button.
 */
data class BottomAlignment(val gap: Int, val keepBottomSpace: Boolean)

/**
 * Work out the [BottomAlignment] of a list.
 *
 * @param visibleHeight The height of the list that is on screen. Under an app bar that can collapse
 *   this is what is left while the bar is expanded.
 * @param paddingTop The padding above the items.
 * @param paddingBottomWithoutSpace The padding below the items, without the space kept for a
 *   floating button.
 * @param contentHeight The height of all the items together.
 */
fun bottomAlignment(
    visibleHeight: Int,
    paddingTop: Int,
    paddingBottomWithoutSpace: Int,
    contentHeight: Int,
): BottomAlignment {
    val available = visibleHeight - paddingTop - paddingBottomWithoutSpace
    val gap = (available - contentHeight).coerceAtLeast(0)
    return BottomAlignment(gap, keepBottomSpace = gap == 0)
}
