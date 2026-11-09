/*
 * Copyright (c) 2026 Auxio Project
 * LangUtilTest.kt is part of Auxio.
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
 
package org.oxycblt.musikr.util

import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.oxycblt.musikr.tag.Date

class LangUtilTest {
    @Test
    fun onlyPositiveNumbersSurvivePositiveOrNull() {
        assertEquals(3, 3.positiveOrNull())
        assertNull(0.positiveOrNull())
        assertNull((-4).positiveOrNull())
    }

    @Test
    fun zeroIsNoFloat() {
        assertEquals(1.5f, 1.5f.nonZeroOrNull())
        assertEquals(-2f, (-2f).nonZeroOrNull())
        assertNull(0f.nonZeroOrNull())
    }

    @Test
    fun aNumberOutsideTheRangeIsNull() {
        assertEquals(5, 5.inRangeOrNull(1..10))
        assertNull(11.inRangeOrNull(1..10))
        assertNull(0.inRangeOrNull(1..10))
    }

    @Test
    fun uuidsAreParsedOrNull() {
        val id = UUID.randomUUID()
        assertEquals(id, id.toString().toUuidOrNull())
        assertNull("not a uuid".toUuidOrNull())
        assertNull("".toUuidOrNull())
    }

    @Test
    fun aValueThatCannotBeNullIsReturnedAndNullIsAnError() {
        assertEquals("x", unlikelyToBeNull("x"))
        assertThrows(Exception::class.java) { unlikelyToBeNull<String>(null) }
    }

    private fun hash(update: MessageDigest.() -> Unit) =
        MessageDigest.getInstance("SHA-256").apply(update).digest().toList()

    @Test
    fun digestsOfTheSameValuesAreTheSameAndOtherValuesDiffer() {
        assertEquals(hash { update("a" as String?) }, hash { update("a" as String?) })
        assertNotEquals(hash { update("a" as String?) }, hash { update("b" as String?) })
        assertEquals(hash { update(7 as Int?) }, hash { update(7 as Int?) })
        assertNotEquals(hash { update(7 as Int?) }, hash { update(8 as Int?) })
        assertEquals(
            hash { update(listOf("a", null, "b")) },
            hash { update(listOf("a", null, "b")) },
        )
        assertNotEquals(hash { update(listOf("a", "b")) }, hash { update(listOf("b", "a")) })
    }

    @Test
    fun missingValuesMakeADifferentDigestThanPresentOnes() {
        assertNotEquals(hash { update(null as String?) }, hash { update("x" as String?) })
        assertNotEquals(hash { update(null as Int?) }, hash { update(0 as Int?) })
        assertNotEquals(hash { update(null as Date?) }, hash { update(Date.from(2020)) })
        assertEquals(hash { update(Date.from(2020)) }, hash { update(Date.from(2020)) })
    }
}
