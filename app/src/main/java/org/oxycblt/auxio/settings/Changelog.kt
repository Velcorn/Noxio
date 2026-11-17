/*
 * Copyright (c) 2026 Auxio Project
 * Changelog.kt is part of Auxio.
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

/** One release of the changelog: its [version] and its [sections] (What's New and so on). */
data class ChangelogRelease(val version: String, val sections: List<ChangelogSection>)

/** A titled group of [items] in a [ChangelogRelease]. */
data class ChangelogSection(val title: String, val items: List<String>)

/**
 * Reads `CHANGELOG.md`, which is bundled into the app at build time. Releases are `## vX.Y.Z`,
 * their sections are the other `## ` headings, and the points are `- ` lines that may continue on
 * indented lines. Anything before the first release is ignored.
 */
object Changelog {
    /** Name of the bundled file in the app's assets. */
    const val ASSET = "changelog.md"

    private val VERSION = Regex("""^##\s+v?(\d+(\.\d+)+\S*)\s*$""")

    /** The releases in [text], in the order of the file (newest first). */
    fun parse(text: String): List<ChangelogRelease> {
        val releases = mutableListOf<ChangelogRelease>()
        var version: String? = null
        var sections = mutableListOf<ChangelogSection>()
        var title: String? = null
        var items = mutableListOf<String>()

        fun closeSection() {
            val sectionTitle = title
            if (sectionTitle != null && items.isNotEmpty()) {
                sections.add(ChangelogSection(sectionTitle, items))
            }
            title = null
            items = mutableListOf()
        }

        fun closeRelease() {
            closeSection()
            version?.let { releases.add(ChangelogRelease(it, sections)) }
            version = null
            sections = mutableListOf()
        }

        for (line in text.lines()) {
            val release = VERSION.matchEntire(line.trimEnd())
            when {
                release != null -> {
                    closeRelease()
                    version = release.groupValues[1]
                }
                version == null -> {}
                line.startsWith("## ") -> {
                    closeSection()
                    title = line.removePrefix("## ").trim()
                }
                line.startsWith("- ") -> items.add(plain(line.removePrefix("- ")))
                line.isNotBlank() && line.first().isWhitespace() && items.isNotEmpty() ->
                    items[items.lastIndex] = items.last() + " " + plain(line.trim())
            }
        }
        closeRelease()
        return releases
    }

    /** Drop the Markdown emphasis and code marks, which the screen does not draw. */
    private fun plain(text: String) = text.trim().replace("*", "").replace("`", "")
}
