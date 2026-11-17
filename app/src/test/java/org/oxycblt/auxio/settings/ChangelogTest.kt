/*
 * Copyright (c) 2026 Auxio Project
 * ChangelogTest.kt is part of Auxio.
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

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTest {
    private val sample =
        """
        # Noxio changelog

        Some text before the first release.

        ## v1.2.0

        ## What's New
        - A *new* thing with `code`
        - A long point that
          goes on here

        ## What's Fixed
        - A fix

        ## v1.1.0

        ## What's Changed
        - Something changed
        """
            .trimIndent()

    @Test
    fun readsReleasesNewestFirstInFileOrder() {
        assertEquals(listOf("1.2.0", "1.1.0"), Changelog.parse(sample).map { it.version })
    }

    @Test
    fun readsSectionsAndPoints() {
        val first = Changelog.parse(sample).first()
        assertEquals(listOf("What's New", "What's Fixed"), first.sections.map { it.title })
        assertEquals("A fix", first.sections[1].items.single())
    }

    @Test
    fun dropsMarkdownMarksAndJoinsContinuedPoints() {
        val items = Changelog.parse(sample).first().sections.first().items
        assertEquals(listOf("A new thing with code", "A long point that goes on here"), items)
    }

    @Test
    fun ignoresWhatComesBeforeTheFirstRelease() {
        assertEquals(2, Changelog.parse(sample).size)
        assertTrue(Changelog.parse("# Title\n\nJust text").isEmpty())
    }

    @Test
    fun anEmptySectionIsLeftOut() {
        val releases = Changelog.parse("## v1.0.0\n\n## What's New\n\n## What's Fixed\n- x")
        assertEquals(listOf("What's Fixed"), releases.single().sections.map { it.title })
    }

    @Test
    fun theChangelogOfTheRepositoryReadsAndEveryReleaseHasPoints() {
        val file = listOf(File("../CHANGELOG.md"), File("CHANGELOG.md")).first { it.exists() }
        val releases = Changelog.parse(file.readText())
        assertTrue(releases.size > 10)
        assertEquals(releases.map { it.version }.distinct(), releases.map { it.version })
        releases.forEach { release ->
            assertTrue("${release.version} has no points", release.sections.isNotEmpty())
        }
    }
}
