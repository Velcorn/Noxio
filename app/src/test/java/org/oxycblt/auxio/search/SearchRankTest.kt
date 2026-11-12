/*
 * Copyright (c) 2026 Auxio Project
 * SearchRankTest.kt is part of Auxio.
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which names are the better match for what was searched for. */
class SearchRankTest {
    @Test
    fun theLevelsFromBestToWorst() {
        assertEquals(SearchRank.EXACT, SearchRank.rank("Moth", "moth"))
        assertEquals(SearchRank.PREFIX, SearchRank.rank("Mothership", "moth"))
        assertEquals(SearchRank.WORD, SearchRank.rank("Night Moth", "moth"))
        assertEquals(SearchRank.CONTAINS, SearchRank.rank("Behemoth", "moth"))
        assertEquals(SearchRank.WORDS, SearchRank.rank("Owls of the Night", "night owls"))
        assertEquals(SearchRank.NONE, SearchRank.rank("Kestrel", "moth"))
        assertTrue(SearchRank.EXACT < SearchRank.PREFIX)
        assertTrue(SearchRank.PREFIX < SearchRank.WORD)
        assertTrue(SearchRank.WORD < SearchRank.CONTAINS)
        assertTrue(SearchRank.CONTAINS < SearchRank.WORDS)
        assertTrue(SearchRank.WORDS < SearchRank.OTHER)
        assertTrue(SearchRank.OTHER < SearchRank.NONE)
    }

    @Test
    fun aWordStartsAfterAnythingThatIsNotALetterOrDigit() {
        assertEquals(SearchRank.WORD, SearchRank.rank("Low-Battery", "battery"))
        assertEquals(SearchRank.WORD, SearchRank.rank("(Live) Moth", "live"))
        // A later word is found even if the first place where it appears is inside another word.
        assertEquals(SearchRank.WORD, SearchRank.rank("Behemoth moth", "moth"))
    }

    @Test
    fun theQueryIsTrimmedAndCaseDoesNotMatter() {
        assertEquals(SearchRank.EXACT, SearchRank.rank("MOTH", "  moth "))
        assertEquals(SearchRank.PREFIX, SearchRank.rank("moth ball", "MOTH"))
    }

    @Test
    fun everyWordMustBeThereForSeveralWords() {
        assertEquals(SearchRank.NONE, SearchRank.rank("Owls of the Day", "night owls"))
        // One word is not a "several words" match.
        assertEquals(SearchRank.NONE, SearchRank.rank("Kestrel", "moth"))
    }

    @Test
    fun anEmptyQueryMatchesEverything() {
        assertEquals(SearchRank.CONTAINS, SearchRank.rank("Anything", ""))
        assertEquals(SearchRank.CONTAINS, SearchRank.rank("Anything", "   "))
    }

    @Test
    fun theBestOfSeveralNamesCounts() {
        assertEquals(
            SearchRank.PREFIX,
            SearchRank.best(listOf("The Night Owls", "Night Owls, The"), "night"),
        )
        assertEquals(
            SearchRank.EXACT,
            SearchRank.best(listOf("Mötley Crüe", null, "Motley Crue"), "motley crue"),
        )
        assertEquals(SearchRank.NONE, SearchRank.best(listOf(null, null), "x"))
        assertEquals(SearchRank.NONE, SearchRank.best(emptyList(), "x"))
    }
}
