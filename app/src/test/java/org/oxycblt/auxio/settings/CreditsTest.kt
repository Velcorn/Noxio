/*
 * Copyright (c) 2026 Auxio Project
 * CreditsTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The library reads its JSON with Android's org.json, so this needs Robolectric.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreditsTest {
    private fun lib(
        id: String,
        name: String,
        licenses: List<String>,
        developer: String? = "Dev",
        organization: String? = null,
    ) =
        """{"uniqueId":"$id","artifactVersion":"1","name":"$name",
        "developers":[${developer?.let { """{"name":"$it"}""" } ?: ""}],
        ${organization?.let { """"organization":{"name":"$it"},""" } ?: ""}
        "website":"https://example.org/$name","licenses":${licenses.joinToString(",", "[", "]") { "\"$it\"" }}}"""

    private fun json(vararg libs: String) =
        """{"libraries":[${libs.joinToString(",")}],"licenses":{
        "Apache-2.0":{"name":"Apache License 2.0","url":"https://apache.org","content":"Apache text","hash":"Apache-2.0"},
        "GPL-3.0-or-later":{"name":"GPL 3","url":"https://gnu.org","content":"GPL text","hash":"GPL-3.0-or-later"},
        "hash1":{"name":"Some License","url":"https://example.org/l","hash":"hash1"}}}"""

    @Test
    fun foundationsComeFirstInTheirOrderAndTheRestAToZ() {
        val credits =
            CreditsList.parse(
                json(
                    lib("b:zeta", "zeta", listOf("Apache-2.0")),
                    lib("org.taglib:taglib", "TagLib", listOf("Apache-2.0")),
                    lib("a:alpha", "Alpha", listOf("Apache-2.0")),
                    lib("org.oxycblt:auxio", "Auxio", listOf("GPL-3.0-or-later")),
                ),
            )
        assertEquals(listOf("Auxio", "TagLib"), credits.foundations.map { it.name })
        assertEquals(listOf("Alpha", "zeta"), credits.libraries.map { it.name })
        assertEquals("GPL text", credits.foundations[0].licenses.single().text)
        assertEquals("https://example.org/Alpha", credits.libraries[0].website)
    }

    @Test
    fun licenseWithoutTextIsDroppedOnlyWhenAnotherHasText() {
        val credits =
            CreditsList.parse(
                json(
                    lib("x:fixed", "Fixed", listOf("hash1", "Apache-2.0")),
                    lib("x:only", "Only", listOf("hash1")),
                ),
            )
        assertEquals(listOf("Apache License 2.0"), credits.libraries[0].licenses.map { it.name })
        assertEquals(listOf("Some License"), credits.libraries[1].licenses.map { it.name })
        assertTrue(credits.libraries[1].licenses[0].text.isEmpty())
    }

    @Test
    fun sameNameIsListedOnceAndAuthorFallsBackToOrganization() {
        val credits =
            CreditsList.parse(
                json(
                    lib("x:one", "Room", listOf("Apache-2.0")),
                    lib("x:two", "room", listOf("Apache-2.0")),
                    lib(
                        "y:org",
                        "Org",
                        listOf("Apache-2.0"),
                        developer = null,
                        organization = "ACME",
                    ),
                ),
            )
        assertEquals(listOf("Org", "Room"), credits.libraries.map { it.name })
        assertEquals("ACME", credits.libraries[0].by)
    }

    @Test
    fun androidxPartsAreFoldedIntoOneEntry() {
        val credits =
            CreditsList.parse(
                json(
                    lib("androidx.core:core", "Core", listOf("Apache-2.0"), developer = "AOSP"),
                    lib("androidx.room:room", "Room", listOf("Apache-2.0"), developer = "AOSP"),
                    lib("b:beta", "Beta", listOf("Apache-2.0")),
                ),
            )
        assertEquals(listOf("AndroidX", "Beta"), credits.libraries.map { it.name })
        assertEquals("AOSP", credits.libraries[0].by)
        assertEquals(CreditsList.ANDROIDX_WEBSITE, credits.libraries[0].website)
        assertEquals(1, credits.libraries[0].licenses.size)
    }
}
