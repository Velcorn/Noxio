/*
 * Copyright (c) 2026 Auxio Project
 * BottomAlignedPreferenceLayoutManager.kt is part of Auxio.
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
 
package org.oxycblt.auxio.settings.ui

import android.content.Context
import android.graphics.Rect
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.oxycblt.auxio.list.recycler.bottomAlignment

/** Places short preference lists at the bottom without opening long lists at their end. */
class BottomAlignedPreferenceLayoutManager(context: Context) : LinearLayoutManager(context) {
    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        super.onLayoutChildren(recycler, state)
        if (state.isPreLayout || state.itemCount == 0) return
        val first = findViewByPosition(0) ?: return
        val firstBounds = Rect()
        getDecoratedBoundsWithMargins(first, firstBounds)
        val last = findViewByPosition(state.itemCount - 1)
        val gap =
            if (last != null) {
                val lastBounds = Rect()
                getDecoratedBoundsWithMargins(last, lastBounds)
                bottomAlignment(
                        height,
                        paddingTop,
                        paddingBottom,
                        lastBounds.bottom - firstBounds.top,
                    )
                    .gap
            } else 0
        if (gap > 0) {
            offsetChildrenVertical(paddingTop + gap - firstBounds.top)
        } else if (firstBounds.top > paddingTop) {
            // A list that previously fitted may grow or lose space after rotation/font changes.
            scrollVerticallyBy(firstBounds.top - paddingTop, recycler, state)
        }
    }
}
