/*
 * Copyright (c) 2026 Auxio Project
 * SearchRank.kt is part of Auxio.
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
 
package org.oxycblt.auxio.search

/**
 * How well a name matches what was searched for, so the best matches come first: the name itself, a
 * name that starts with it, a word that starts with it, then the name that has it somewhere, and
 * last a name that has all of its words in any order.
 */
object SearchRank {
    /** The name is what was searched for. */
    const val EXACT = 0
    /** The name starts with what was searched for. */
    const val PREFIX = 1
    /** A word of the name starts with what was searched for. */
    const val WORD = 2
    /** The name has what was searched for somewhere in it. */
    const val CONTAINS = 3
    /** The name has every word of what was searched for, in any order. */
    const val WORDS = 4
    /** The name does not match, but something else about the item does (its file name). */
    const val OTHER = 5
    /** The name does not match. */
    const val NONE = Int.MAX_VALUE

    /** The rank of [name] for [query], [NONE] if it does not match. Lower is better. */
    fun rank(name: String, query: String): Int {
        val needle = query.trim()
        return when {
            needle.isEmpty() -> CONTAINS
            name.equals(needle, ignoreCase = true) -> EXACT
            name.startsWith(needle, ignoreCase = true) -> PREFIX
            startsAWord(name, needle) -> WORD
            name.contains(needle, ignoreCase = true) -> CONTAINS
            hasAllWords(name, needle) -> WORDS
            else -> NONE
        }
    }

    /** The best rank of any of the [names], for the name, its sort name and its plain letters. */
    fun best(names: List<String?>, query: String): Int =
        names.minOfOrNull { if (it == null) NONE else rank(it, query) } ?: NONE

    private fun startsAWord(name: String, needle: String): Boolean {
        var from = 0
        while (true) {
            val at = name.indexOf(needle, from, ignoreCase = true)
            if (at < 0) return false
            if (at == 0 || !name[at - 1].isLetterOrDigit()) return true
            from = at + 1
        }
    }

    private fun hasAllWords(name: String, needle: String): Boolean {
        val words = needle.split(WHITESPACE).filter { it.isNotEmpty() }
        return words.size > 1 && words.all { name.contains(it, ignoreCase = true) }
    }

    private val WHITESPACE = Regex("\\s+")
}
