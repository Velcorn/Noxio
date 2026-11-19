/*
 * Copyright (c) 2026 Auxio Project
 * TextWrap.kt is part of Auxio.
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

/**
 * Splits a name into at most [maxLines] lines for the share cards, which are drawn onto an image
 * and cannot wrap by themselves. Lines break at spaces; what does not fit in the last line is cut
 * off with an ellipsis.
 */
object TextWrap {
    private const val ELLIPSIS = "…"

    /**
     * @param value The text to split.
     * @param maxLines How many lines at most (at least 1).
     * @param maxWidth How wide a line may be.
     * @param measure The width of a text, for example `paint::measureText`.
     * @return One to [maxLines] lines, each at most [maxWidth] wide. Empty for an empty [value].
     */
    fun wrap(
        value: String,
        maxLines: Int,
        maxWidth: Float,
        measure: (String) -> Float,
    ): List<String> {
        val text = value.trim()
        if (text.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var rest = text
        while (rest.isNotEmpty() && lines.size < maxLines - 1) {
            if (measure(rest) <= maxWidth) {
                lines += rest
                return lines
            }
            val cut = breakAt(rest, maxWidth, measure)
            lines += rest.substring(0, cut).trimEnd()
            rest = rest.substring(cut).trimStart()
        }
        if (rest.isNotEmpty()) {
            lines += cutOff(rest, maxWidth, measure)
        }
        return lines
    }

    /** Lines of a name and whether they are in the smaller size. */
    data class Fit(val lines: List<String>, val small: Boolean)

    /**
     * Fits a name into [maxWidth]: on one line in the normal size if it fits, else on one line in
     * the smaller size, else wrapped to [maxLines] lines in the smaller size.
     *
     * @param measureNormal The width of a text in the normal size.
     * @param measureSmall The width of a text in the smaller size.
     */
    fun shrinkThenWrap(
        value: String,
        maxLines: Int,
        maxWidth: Float,
        measureNormal: (String) -> Float,
        measureSmall: (String) -> Float,
    ): Fit {
        val normal = wrap(value, 1, maxWidth, measureNormal)
        if (normal.isEmpty() || measureNormal(value.trim()) <= maxWidth) return Fit(normal, false)
        return Fit(wrap(value, maxLines, maxWidth, measureSmall), true)
    }

    /** Where the first line of [text] ends: after the last space that fits, else mid-word. */
    private fun breakAt(text: String, maxWidth: Float, measure: (String) -> Float): Int {
        var fit = 0
        for (end in 1..text.length) {
            if (measure(text.substring(0, end)) > maxWidth) break
            fit = end
        }
        if (fit == 0) fit = 1
        if (fit == text.length) return fit
        val space = text.lastIndexOf(' ', fit)
        // A break at a space, unless that would leave the line almost empty.
        return if (space > fit / 3) space + 1 else fit
    }

    private fun cutOff(text: String, maxWidth: Float, measure: (String) -> Float): String {
        if (measure(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && measure(text.substring(0, end).trimEnd() + ELLIPSIS) > maxWidth) {
            end--
        }
        return text.substring(0, end).trimEnd() + ELLIPSIS
    }
}
