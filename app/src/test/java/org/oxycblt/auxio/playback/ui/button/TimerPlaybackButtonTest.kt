/*
 * Copyright (c) 2026 Auxio Project
 * TimerPlaybackButtonTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.ui.button

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimerPlaybackButtonTest {
    @Test
    fun countdownDrawingKeepsTransportGeometryAndRestoresTheIconForNormalPlayback() {
        val context =
            ContextThemeWrapper(
                ApplicationProvider.getApplicationContext<Context>(),
                R.style.Theme_Auxio_App,
            )
        val root = LayoutInflater.from(context).inflate(R.layout.fragment_playback_panel, null)
        val button = root.findViewById<TimerPlaybackButton>(R.id.playback_play_pause)
        val width = button.baseWidth
        val height = button.baseHeight
        val spec = View.MeasureSpec.makeMeasureSpec(220, View.MeasureSpec.EXACTLY)
        button.measure(spec, spec)
        button.layout(0, 0, 220, 220)
        val canvas = Canvas(Bitmap.createBitmap(220, 220, Bitmap.Config.ARGB_8888))
        assertNotNull(button.icon)
        for (playing in listOf(false, true)) {
            button.isChecked = playing
            button.draw(canvas)
            val originalBounds = Rect(button.icon!!.bounds)
            for (time in listOf("24:38", "1:40:00", "9:59:00", null)) {
                button.countdown = time
                button.draw(canvas)
                assertEquals(originalBounds, button.icon!!.bounds)
                assertEquals(width, button.baseWidth)
                assertEquals(height, button.baseHeight)
                assertEquals(220, button.measuredWidth)
                assertEquals(220, button.measuredHeight)
            }
        }
    }
}
