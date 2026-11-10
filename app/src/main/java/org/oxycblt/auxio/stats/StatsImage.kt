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

/** What goes onto the image of a single artist, album or song. */
data class StatsItemImageContent(
    val category: StatsCategory,
    val name: String,
    /** Up to two lines under the name, for example the artist and the period. */
    val lines: List<String>,
    /** Up to four numbers with what they are, shown as tiles. */
    val tiles: List<Tile>,
    /** The heading of the lower half, which has either [rows] or [bars]. */
    val sectionTitle: String,
    val rows: List<StatsImageContent.Row>,
    val bars: List<Bar>,
    val footerStart: String,
    val footerEnd: String,
) {
    data class Tile(val value: String, val label: String)

    /**
     * A bar of the chart.
     *
     * @param fraction Its height, from 0 to 1.
     * @param label What is written under it. Null for nothing.
     */
    data class Bar(val fraction: Float, val label: String?)
}

/** Draws the shareable card as a portrait bitmap. The size does not depend on screen density. */
object StatsImage {
    const val WIDTH = 1080
    const val HEIGHT = 1350

    private const val MARGIN = 80f
    private const val FIRST_ROW_Y = 400f
    private const val ROW_HEIGHT = 150f

    private const val CONTENT_WIDTH = WIDTH - 2 * MARGIN
    private const val FIRST_TILE_Y = 410f
    private const val TILE_HEIGHT = 136f
    private const val TILE_GAP = 20f
    private const val FOOTER_GAP = 40f

    /** The smallest the footer texts get before the start is cut off. */
    const val MIN_FOOTER_SCALE = 0.7f

    /**
     * How much the two footer texts are scaled so that [width] of text fits into [available]: 1 if
     * it fits, never less than [MIN_FOOTER_SCALE].
     */
    fun footerScale(width: Float, available: Float) =
        if (width <= available) 1f else (available / width).coerceAtLeast(MIN_FOOTER_SCALE)

    private const val TILE_WIDTH = (CONTENT_WIDTH - TILE_GAP) / 2
    private const val SECTION_Y = 780f
    private const val ITEM_ROW_HEIGHT = 112f
    private const val BAR_HEIGHT = 230f

    fun render(context: Context, content: StatsImageContent): Bitmap {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
        drawBackground(context, canvas, paint, text, content.category)

        text.alpha = 255
        text.textSize = 96f
        canvas.drawText(content.title, MARGIN, 250f, text)

        text.typeface = Typeface.DEFAULT
        text.textSize = 40f
        text.alpha = 230
        canvas.drawText(content.rangeLabel, MARGIN, 312f, text)

        content.rows.forEachIndexed { index, row ->
            drawRow(canvas, paint, text, index, FIRST_ROW_Y + index * ROW_HEIGHT, row)
        }

        drawFooter(canvas, paint, text, content.totalTime, content.totalPlays)
        return bitmap
    }

    /** Draw the card of a single artist, album or song. */
    fun renderItem(context: Context, content: StatsItemImageContent): Bitmap {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
        drawBackground(context, canvas, paint, text, content.category)

        // A long name is written smaller before it is cut off.
        text.alpha = 255
        text.textSize = 96f
        if (text.measureText(content.name) > CONTENT_WIDTH) {
            text.textSize = 68f
        }
        canvas.drawText(ellipsize(content.name, text, CONTENT_WIDTH), MARGIN, 250f, text)

        text.typeface = Typeface.DEFAULT
        text.textSize = 40f
        text.alpha = 230
        content.lines.take(2).forEachIndexed { index, line ->
            canvas.drawText(ellipsize(line, text, CONTENT_WIDTH), MARGIN, 312f + index * 54f, text)
        }
        text.alpha = 255

        content.tiles.take(4).forEachIndexed { index, tile ->
            val left = MARGIN + (index % 2) * (TILE_WIDTH + TILE_GAP)
            val top = FIRST_TILE_Y + (index / 2) * (TILE_HEIGHT + TILE_GAP)
            paint.color = 0x33FFFFFF
            canvas.drawRoundRect(
                RectF(left, top, left + TILE_WIDTH, top + TILE_HEIGHT),
                36f,
                36f,
                paint,
            )
            text.typeface = Typeface.DEFAULT_BOLD
            text.textSize = 54f
            canvas.drawText(
                ellipsize(tile.value, text, TILE_WIDTH - 72f),
                left + 36f,
                top + 66f,
                text,
            )
            text.typeface = Typeface.DEFAULT
            text.textSize = 30f
            text.alpha = 204
            canvas.drawText(
                ellipsize(tile.label, text, TILE_WIDTH - 72f),
                left + 36f,
                top + 108f,
                text,
            )
            text.alpha = 255
        }

        if (content.rows.isNotEmpty() || content.bars.isNotEmpty()) {
            text.typeface = Typeface.DEFAULT_BOLD
            text.textSize = 34f
            text.alpha = 217
            canvas.drawText(content.sectionTitle.uppercase(), MARGIN, SECTION_Y, text)
            text.alpha = 255
        }
        content.rows.take(3).forEachIndexed { index, row ->
            drawRow(canvas, paint, text, index, SECTION_Y + 44f + index * ITEM_ROW_HEIGHT, row)
        }
        if (content.rows.isEmpty()) {
            drawBars(canvas, paint, text, content.bars)
        }

        if (content.footerStart.isNotEmpty() || content.footerEnd.isNotEmpty()) {
            drawFooter(canvas, paint, text, content.footerStart, content.footerEnd, small = true)
        }
        return bitmap
    }

    /** The gradient, the two circles and the name of the app. Leaves [text] white and bold. */
    private fun drawBackground(
        context: Context,
        canvas: Canvas,
        paint: Paint,
        text: TextPaint,
        category: StatsCategory,
    ) {
        val (start, end) = gradientOf(context, category)
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

        text.color = WHITE
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 34f
        text.alpha = 217
        canvas.drawText("NOXIO", MARGIN, 120f, text)
    }

    /**
     * The rounded strip at the bottom with one text at each end.
     *
     * @param small Whether both texts are written in the smaller size, as they are longer.
     */
    private fun drawFooter(
        canvas: Canvas,
        paint: Paint,
        text: TextPaint,
        start: String,
        end: String,
        small: Boolean = false,
    ) {
        paint.color = 0x33FFFFFF
        canvas.drawRoundRect(
            RectF(MARGIN, HEIGHT - 190f, WIDTH - MARGIN, HEIGHT - 80f),
            55f,
            55f,
            paint,
        )
        text.alpha = 255
        val startSize = if (small) 36f else 44f
        val endSize = 36f
        // Both texts share the line. When they are too long together (longer languages), both get
        // smaller, and the start is cut off if even that is not enough.
        val available = WIDTH - 2 * MARGIN - 88f - FOOTER_GAP
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = startSize
        val startWidth = text.measureText(start)
        text.typeface = Typeface.DEFAULT
        text.textSize = endSize
        val endWidth = text.measureText(end)
        val scale = footerScale(startWidth + endWidth, available)

        text.typeface = Typeface.DEFAULT
        text.textSize = endSize * scale
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(end, WIDTH - MARGIN - 44f, HEIGHT - 120f, text)
        val endDrawn = text.measureText(end)
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = startSize * scale
        text.textAlign = Paint.Align.LEFT
        canvas.drawText(
            ellipsize(start, text, available - endDrawn),
            MARGIN + 44f,
            HEIGHT - 120f,
            text,
        )
    }

    /** A bar chart in the lower half. Every bar is at least a thin line, so none is missing. */
    private fun drawBars(
        canvas: Canvas,
        paint: Paint,
        text: TextPaint,
        bars: List<StatsItemImageContent.Bar>,
    ) {
        if (bars.isEmpty()) return
        val top = SECTION_Y + 50f
        val bottom = top + BAR_HEIGHT
        val slot = CONTENT_WIDTH / bars.size
        val width = (slot * 0.7f).coerceAtMost(90f)
        text.typeface = Typeface.DEFAULT
        text.textSize = 28f
        text.textAlign = Paint.Align.CENTER
        text.alpha = 204
        bars.forEachIndexed { index, bar ->
            val center = MARGIN + slot * (index + 0.5f)
            val height = (BAR_HEIGHT * bar.fraction.coerceIn(0f, 1f)).coerceAtLeast(6f)
            paint.color = if (bar.fraction > 0f) WHITE else 0x55FFFFFF
            canvas.drawRoundRect(
                RectF(center - width / 2, bottom - height, center + width / 2, bottom),
                width / 4,
                width / 4,
                paint,
            )
            if (bar.label != null) {
                // The labels at the two ends are kept inside the image.
                val half = text.measureText(bar.label) / 2
                val x = center.coerceIn(MARGIN + half, WIDTH - MARGIN - half)
                canvas.drawText(bar.label, x, bottom + 44f, text)
            }
        }
        text.alpha = 255
        text.textAlign = Paint.Align.LEFT
    }

    private fun drawRow(
        canvas: Canvas,
        paint: Paint,
        text: TextPaint,
        index: Int,
        top: Float,
        row: StatsImageContent.Row,
    ) {
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
        // A name without a second line sits in the middle of its row, next to the number.
        val nameY = if (row.subtitle.isNullOrEmpty()) top + 55f else top + 36f
        canvas.drawText(ellipsize(row.name, text, 560f), MARGIN + 120f, nameY, text)
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
