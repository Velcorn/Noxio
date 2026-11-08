/*
 * Copyright (c) 2026 Auxio Project
 * StatsImage.kt is part of Auxio.
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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import org.oxycblt.auxio.R

/** What goes onto the image that can be shared from the stats screen. */
data class StatsImageContent(
    val category: StatsCategory,
    val title: String,
    val rangeLabel: String,
    val rows: List<Row>,
    val totalTime: String,
    val totalPlays: String,
) {
    data class Row(val name: String, val subtitle: String?, val plays: String, val time: String)
}

/** Draws the shareable card as a portrait bitmap. The size does not depend on screen density. */
object StatsImage {
    const val WIDTH = 1080
    const val HEIGHT = 1350

    private const val MARGIN = 80f
    private const val FIRST_ROW_Y = 400f
    private const val ROW_HEIGHT = 150f

    fun render(context: Context, content: StatsImageContent): Bitmap {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        val (start, end) = gradientOf(context, content.category)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader =
            LinearGradient(
                0f,
                0f,
                WIDTH.toFloat(),
                HEIGHT.toFloat(),
                start,
                end,
                Shader.TileMode.CLAMP,
            )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        paint.shader = null

        // Two soft circles for some depth.
        paint.color = 0x14FFFFFF
        canvas.drawCircle(WIDTH - 120f, 140f, 320f, paint)
        canvas.drawCircle(80f, HEIGHT - 60f, 260f, paint)

        val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
        text.color = WHITE

        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 34f
        text.alpha = 217
        canvas.drawText("NOXIO", MARGIN, 120f, text)

        text.alpha = 255
        text.textSize = 96f
        canvas.drawText(content.title, MARGIN, 250f, text)

        text.typeface = Typeface.DEFAULT
        text.textSize = 40f
        text.alpha = 230
        canvas.drawText(content.rangeLabel, MARGIN, 312f, text)

        content.rows.forEachIndexed { index, row ->
            drawRow(canvas, paint, text, index, row)
        }

        // Footer with the totals.
        paint.color = 0x33FFFFFF
        canvas.drawRoundRect(
            RectF(MARGIN, HEIGHT - 190f, WIDTH - MARGIN, HEIGHT - 80f),
            55f,
            55f,
            paint,
        )
        text.alpha = 255
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 44f
        text.textAlign = Paint.Align.LEFT
        canvas.drawText(content.totalTime, MARGIN + 44f, HEIGHT - 120f, text)
        text.typeface = Typeface.DEFAULT
        text.textSize = 36f
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(content.totalPlays, WIDTH - MARGIN - 44f, HEIGHT - 120f, text)
        return bitmap
    }

    private fun drawRow(
        canvas: Canvas,
        paint: Paint,
        text: TextPaint,
        index: Int,
        row: StatsImageContent.Row,
    ) {
        val top = FIRST_ROW_Y + index * ROW_HEIGHT
        val first = index == 0

        paint.color = if (first) WHITE else 0x38FFFFFF
        canvas.drawCircle(MARGIN + 44f, top + 40f, 40f, paint)

        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 40f
        text.textAlign = Paint.Align.CENTER
        text.color = if (first) DARK else WHITE
        canvas.drawText((index + 1).toString(), MARGIN + 44f, top + 54f, text)

        text.color = WHITE
        text.textAlign = Paint.Align.LEFT
        text.textSize = 44f
        canvas.drawText(ellipsize(row.name, text, 560f), MARGIN + 120f, top + 36f, text)
        if (!row.subtitle.isNullOrEmpty()) {
            text.typeface = Typeface.DEFAULT
            text.textSize = 32f
            text.alpha = 204
            canvas.drawText(ellipsize(row.subtitle, text, 560f), MARGIN + 120f, top + 80f, text)
            text.alpha = 255
        }

        text.textAlign = Paint.Align.RIGHT
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 38f
        canvas.drawText(row.plays, WIDTH - MARGIN, top + 36f, text)
        text.typeface = Typeface.DEFAULT
        text.textSize = 32f
        text.alpha = 204
        canvas.drawText(row.time, WIDTH - MARGIN, top + 80f, text)
        text.alpha = 255
        text.textAlign = Paint.Align.LEFT
    }

    private fun ellipsize(value: String, paint: TextPaint, maxWidth: Float) =
        TextUtils.ellipsize(value, paint, maxWidth, TextUtils.TruncateAt.END).toString()

    /** The start and end color of a category's gradient. */
    fun gradientOf(context: Context, category: StatsCategory): Pair<Int, Int> =
        when (category) {
            StatsCategory.ARTISTS ->
                ContextCompat.getColor(context, R.color.stats_artists_start) to
                    ContextCompat.getColor(context, R.color.stats_artists_end)
            StatsCategory.ALBUMS ->
                ContextCompat.getColor(context, R.color.stats_albums_start) to
                    ContextCompat.getColor(context, R.color.stats_albums_end)
            StatsCategory.SONGS ->
                ContextCompat.getColor(context, R.color.stats_songs_start) to
                    ContextCompat.getColor(context, R.color.stats_songs_end)
        }

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val DARK = 0xFF222222.toInt()
}
