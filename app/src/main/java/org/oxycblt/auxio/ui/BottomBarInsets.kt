/*
 * Copyright (c) 2026 Auxio Project
 * BottomBarInsets.kt is part of Auxio.
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

import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import org.oxycblt.auxio.util.systemBarInsetsCompat

/**
 * Lift a bar at the bottom of a screen above the system navigation (and the playback bar, which the
 * insets of the main screen include), by giving it a bottom margin of the bottom inset.
 *
 * @param onInset Called with the bottom inset, for what has to end where the bar begins.
 */
fun View.liftByBottomInset(onInset: (bottomInset: Int) -> Unit = {}) {
    setOnApplyWindowInsetsListener { view, insets ->
        val inset = insets.systemBarInsetsCompat.bottom
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = inset }
        onInset(inset)
        insets
    }
}
