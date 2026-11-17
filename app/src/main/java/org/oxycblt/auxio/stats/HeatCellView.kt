/*
 * Copyright (c) 2026 Auxio Project
 * HeatCellView.kt is part of Auxio.
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

/**
 * One month or year in the period picker: a rounded cell shaded by [level] like the heatmap, with a
 * [label] and the listening time below it. Cells outside the shown period are dimmed.
 */
class HeatCellView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {
    var label = ""
    var sublabel = ""
    var level = 0
    var selectedPeriod = false

    private val density = resources.displayMetrics.density
    private val primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
    private val outline =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
    private val onSurface =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
    private val onSurfaceVariant =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
    private val onPrimary =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
        }
    private val text =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 14 * resources.displayMetrics.scaledDensity
        }
    private val small =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 11 * resources.displayMetrics.scaledDensity
        }
    private val rect = RectF()

    fun bind(label: String, sublabel: String, level: Int, selected: Boolean) {
        this.label = label
        this.sublabel = sublabel
        this.level = level
        selectedPeriod = selected
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (64 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = 3 * density
        rect.set(inset, inset, width - inset, height - inset)
        val radius = 10 * density
        val dim = if (selectedPeriod) 255 else 89
        if (level == 0) {
            stroke.color = outline
            stroke.alpha = dim
            canvas.drawRoundRect(rect, radius, radius, stroke)
        } else {
            fill.color = primary
            fill.alpha = LEVEL_ALPHA[level] * dim / 255
            canvas.drawRoundRect(rect, radius, radius, fill)
        }
        val color =
            when {
                !selectedPeriod || level == 0 -> onSurfaceVariant
                level <= 2 -> onSurface
                else -> onPrimary
            }
        text.color = color
        small.color = color
        text.alpha = if (selectedPeriod) 255 else 153
        small.alpha = text.alpha
        val center = rect.centerY()
        canvas.drawText(label, rect.centerX(), center - 2 * density, text)
        if (sublabel.isNotEmpty()) {
            canvas.drawText(sublabel, rect.centerX(), center + 14 * density, small)
        }
    }

    private companion object {
        val LEVEL_ALPHA = intArrayOf(0, 64, 115, 179, 255)
    }
}
