/*
 * Copyright (c) 2026 Auxio Project
 * TimerPlaybackButton.kt is part of Auxio.
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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import androidx.annotation.AttrRes
import com.google.android.material.R

/** Draws the timer inside the existing transport button without changing its measured size. */
class TimerPlaybackButton
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = R.attr.materialButtonStyle,
) : ScaledPlaybackButton(context, attrs, defStyleAttr) {
    var countdown: String? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val originalIconBounds = Rect()

    private val timerPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }

    override fun onDraw(canvas: Canvas) {
        val time = countdown
        if (time == null) {
            super.onDraw(canvas)
            return
        }
        // View.draw already paints the Material background, ripple and animated shape.
        val tint = iconTint?.getColorForState(drawableState, currentTextColor) ?: currentTextColor
        val size = minOf(iconSize * 0.7f, height * 0.27f).toInt()
        icon?.let { drawable ->
            originalIconBounds.set(drawable.bounds)
            val left = (width - size) / 2
            val top = (height * 0.10f).toInt()
            drawable.setBounds(left, top, left + size, top + size)
            drawable.draw(canvas)
            drawable.bounds = originalIconBounds
        }
        timerPaint.color = tint
        timerPaint.textSize =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20f, resources.displayMetrics)
        // Reserve room for the longest supported label, independent of the current time.
        val widest = timerPaint.measureText("9:59:59")
        if (widest > width * 0.86f) timerPaint.textSize *= width * 0.86f / widest
        val baseline = height * 0.55f - (timerPaint.ascent() + timerPaint.descent()) / 2
        canvas.drawText(time, width / 2f, baseline, timerPaint)
    }
}
