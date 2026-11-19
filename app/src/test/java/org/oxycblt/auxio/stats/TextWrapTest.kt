/*
 * Copyright (c) 2026 Auxio Project
 * TextWrapTest.kt is part of Auxio.
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextWrapTest {
    // One unit per character.
    private val width: (String) -> Float = { it.length.toFloat() }

    @Test
    fun aShortTextStaysOnOneLine() {
        assertEquals(listOf("Glass Coast"), TextWrap.wrap("Glass Coast", 2, 20f, width))
    }

    @Test
    fun aLongTextBreaksAtASpace() {
        assertEquals(
            listOf("Come Over When", "You're Sober"),
            TextWrap.wrap("Come Over When You're Sober", 2, 15f, width),
        )
    }

    @Test
    fun whatDoesNotFitInTheLastLineIsCutOffWithAnEllipsis() {
        val lines =
            TextWrap.wrap("Come Over When You're Sober, Pt. 2 (Deluxe Edition)", 2, 20f, width)
        assertEquals(2, lines.size)
        assertEquals("Come Over When", lines[0])
        assertTrue(lines[1].endsWith("…"))
        assertTrue(lines.all { it.length <= 20 })
    }

    @Test
    fun oneLineIsCutOffAtTheEnd() {
        assertEquals(
            listOf("Come Over When…"),
            TextWrap.wrap("Come Over When You're Sober", 1, 15f, width),
        )
    }

    @Test
    fun aWordLongerThanALineIsBrokenInTheWord() {
        val lines = TextWrap.wrap("Supercalifragilisticexpialidocious", 2, 20f, width)
        assertEquals(2, lines.size)
        assertEquals(20, lines[0].length)
    }

    @Test
    fun emptyTextHasNoLines() {
        assertEquals(emptyList<String>(), TextWrap.wrap("   ", 2, 10f, width))
    }

    @Test
    fun everyLineFitsTheWidth() {
        val lines =
            TextWrap.wrap("Songs for the Night Shift (Live at the Old Mill, 2019)", 2, 24f, width)
        assertTrue(lines.all { it.length <= 24 })
    }

    @Test
    fun shrinkThenWrapPrefersOneSmallerLineOverTwo() {
        // One unit per character in the normal size, 0.8 in the smaller one.
        val normal = { value: String -> value.length.toFloat() }
        val small = { value: String -> value.length * 0.8f }

        assertEquals(
            TextWrap.Fit(listOf("Short name"), false),
            TextWrap.shrinkThenWrap("Short name", 2, 12f, normal, small),
        )
        // 14 characters: too wide normally (14 > 12), fits small (11.2).
        assertEquals(
            TextWrap.Fit(listOf("Diamond Piano!"), true),
            TextWrap.shrinkThenWrap("Diamond Piano!", 2, 12f, normal, small),
        )
        val long = TextWrap.shrinkThenWrap("Diamond Piano Freestyle", 2, 12f, normal, small)
        assertEquals(true, long.small)
        assertEquals(2, long.lines.size)
    }
}
