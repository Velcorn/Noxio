/*
 * Copyright (c) 2026 Auxio Project
 * HeatmapWeekView.kt is part of Auxio.
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
 
package org.oxycblt.auxio.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors
import java.util.Calendar

class HeatmapWeekView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {
    var week: HeatWeek? = null
        set(value) {
            field = value
            invalidate()
        }

    var selectedRange: LongRange = LongRange.EMPTY
        set(value) {
            field = value
            invalidate()
        }

    var onDayClick: ((Long) -> Unit)? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val calendar = Calendar.getInstance()
    private val text =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 12f * resources.displayMetrics.scaledDensity
        }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val density
        get() = resources.displayMetrics.density

    private val primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
    private val outline =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
    private val onSurface =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
    private val onSurfaceVariant =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
    private val onPrimary =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (36 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = week ?: return
        val gap = 4 * density
        val cell = (width - gap * 6) / 7f
        val size = minOf(cell, 28 * density)
        data.days.forEachIndexed { index, day ->
            if (day != null) {
                val left = index * (cell + gap) + (cell - size) / 2
                val top = (height - size) / 2
                val level = day.level.coerceIn(0, 4)
                val selected = day.dayMs in selectedRange
                rect.set(left, top, left + size, top + size)
                if (level == 0) {
                    stroke.color = outline
                    stroke.strokeWidth = density
                    stroke.alpha = if (selected) 255 else 89
                    canvas.drawRoundRect(rect, 5 * density, 5 * density, stroke)
                } else {
                    fill.color = primary
                    fill.alpha =
                        if (selected) listOf(0, 64, 115, 179, 255)[level]
                        else listOf(0, 64, 115, 179, 255)[level] * 89 / 255
                    canvas.drawRoundRect(rect, 5 * density, 5 * density, fill)
                }
                // Dimmed fills are all dark, so their numbers use one readable muted color.
                text.color =
                    when {
                        !selected -> onSurfaceVariant
                        level == 0 -> onSurfaceVariant
                        level <= 2 -> onSurface
                        else -> onPrimary
                    }
                text.alpha = if (selected) 255 else 153
                calendar.timeInMillis = day.dayMs
                val label = calendar.get(Calendar.DAY_OF_MONTH).toString()
                canvas.drawText(
                    label,
                    rect.centerX(),
                    rect.centerY() - (text.ascent() + text.descent()) / 2,
                    text,
                )
            }
        }
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_UP) {
            val gap = 4 * density
            val cell = (width - gap * 6) / 7f
            val index = (event.x / (cell + gap)).toInt().coerceIn(0, 6)
            week?.days?.getOrNull(index)?.let {
                performClick()
                onDayClick?.invoke(it.dayMs)
                return true
            }
        }
        return true
    }
}
