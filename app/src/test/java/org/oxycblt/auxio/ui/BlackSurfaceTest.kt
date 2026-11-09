/*
 * Copyright (c) 2026 Auxio Project
 * BlackSurfaceTest.kt is part of Auxio.
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
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.Insets
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomsheet.BottomSheetDragHandleView
import com.google.android.material.shape.MaterialShapeDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlackSurfaceTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val black = ContextThemeWrapper(app, R.style.Theme_Auxio_Black)
    private val plain = ContextThemeWrapper(app, R.style.Theme_Auxio_Base)

    private fun shape(context: Context) =
        MaterialShapeDrawable()
            .apply { fillColor = android.content.res.ColorStateList.valueOf(Color.RED) }
            .also {
                it.initializeElevationOverlay(context)
            }

    @Test
    fun onlyTheBlackThemeHasABlackSurface() {
        assertTrue(black.isBlackSurface)
        assertFalse(plain.isBlackSurface)
    }

    @Test
    fun aSurfaceBecomesBlackWithABorderInTheBlackTheme() {
        val drawable = shape(black)
        drawable.blackenSurface(black)
        assertEquals(Color.BLACK, drawable.fillColor?.defaultColor)
        assertEquals(1f * black.resources.displayMetrics.density, drawable.strokeWidth, 0.001f)
        assertEquals(0f, drawable.elevation, 0f)
    }

    @Test
    fun otherThemesAndOtherDrawablesAreLeftAlone() {
        val drawable = shape(plain)
        drawable.blackenSurface(plain)
        assertEquals(Color.RED, drawable.fillColor?.defaultColor)
        // Something that is not a shape is ignored, and so is nothing at all.
        android.graphics.drawable.ColorDrawable(Color.RED).blackenSurface(black)
        (null as android.graphics.drawable.Drawable?).blackenSurface(black)
    }

    @Test
    fun theTooltipsOfSheetHandlesAreRemovedAnywhereInAView() {
        val handle = BottomSheetDragHandleView(black)
        TooltipCompat.setTooltipText(handle, "Drag handle")
        val inner = FrameLayout(black).apply { addView(handle) }
        val root = FrameLayout(black).apply { addView(inner) }
        assertEquals("Drag handle", handle.tooltipText)
        root.removeDragHandleTooltips()
        assertNull(handle.tooltipText)
    }

    @Test
    fun otherTooltipsAreKept() {
        val other = View(black)
        TooltipCompat.setTooltipText(other, "Keep me")
        FrameLayout(black).apply { addView(other) }.removeDragHandleTooltips()
        assertEquals("Keep me", other.tooltipText)
    }

    @Test
    fun aBarIsLiftedByTheBottomInsetAndTheOnlookerToldSo() {
        val bar = View(black).apply { layoutParams = FrameLayout.LayoutParams(100, 50) }
        FrameLayout(black).addView(bar)
        var told = -1
        bar.liftByBottomInset { told = it }
        val insets =
            WindowInsets.Builder()
                .setInsets(
                    WindowInsets.Type.systemBars(),
                    android.graphics.Insets.of(0, 10, 0, 120),
                )
                .build()
        bar.dispatchApplyWindowInsets(insets)
        assertEquals(120, (bar.layoutParams as FrameLayout.LayoutParams).bottomMargin)
        assertEquals(120, told)
    }
}
