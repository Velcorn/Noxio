/*
 * Copyright (c) 2026 Auxio Project
 * BlackSurface.kt is part of Auxio.
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

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.TooltipCompat
import com.google.android.material.bottomsheet.BottomSheetDragHandleView
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import org.oxycblt.auxio.R

/**
 * With the black theme (AMOLED) all menus, dialogs and sheets are pure black, set apart from the
 * black screen by a thin dark grey border. These are the helpers that do so, they do nothing with
 * the other themes.
 */
private const val BORDER_COLOR = 0xFF2E2E2E.toInt()

/** Whether the surface color of the current theme is pure black. */
val Context.isBlackSurface: Boolean
    get() =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0) ==
            Color.BLACK

/** Make a surface drawable of a dialog or sheet black with a border, if it is a shape. */
fun Drawable?.blackenSurface(context: Context) {
    if (!context.isBlackSurface) return
    val shape =
        when (this) {
            is MaterialShapeDrawable -> this
            is InsetDrawable -> drawable as? MaterialShapeDrawable
            else -> null
        } ?: return
    shape.fillColor = ColorStateList.valueOf(Color.BLACK)
    shape.setStroke(context.resources.displayMetrics.density, BORDER_COLOR)
    // The elevation overlay would otherwise lighten the black again.
    shape.setElevation(0f)
}

/** Make the window of a dialog black with a border. */
fun Dialog.blackenWindow() {
    window?.decorView?.background.blackenSurface(context)
}

/** Make the sheet of a bottom sheet dialog black with a border. */
fun View.blackenSheet() {
    background.blackenSurface(context)
}

/** A [PopupMenu] that is black with a border when the black theme is used. */
fun popupMenu(context: Context, anchor: View): PopupMenu =
    PopupMenu(
        if (context.isBlackSurface) {
            ContextThemeWrapper(context, R.style.ThemeOverlay_Auxio_BlackPopup)
        } else {
            context
        },
        anchor,
    )

/** Make the window of a dialog black with a border once it is shown. */
fun Dialog.blackenWindowOnShow() = setOnShowListener { blackenWindow() }

/**
 * Remove the tooltips of the drag handles of bottom sheets in this view. Holding a handle to drag
 * the sheet makes the system show the description of the handle ("Drag handle") as a tooltip, which
 * then stays on the screen while the sheet moves.
 */
fun View.removeDragHandleTooltips() {
    if (this is BottomSheetDragHandleView) {
        TooltipCompat.setTooltipText(this, null)
    }
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).removeDragHandleTooltips()
        }
    }
}
