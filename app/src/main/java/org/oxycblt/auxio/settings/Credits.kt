/*
 * Copyright (c) 2026 Auxio Project
 * Credits.kt is part of Auxio.
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

import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library

/** A license as shown in the credits: its name, its full text (may be empty) and a link. */
data class CreditLicense(val name: String, val text: String, val url: String?)

/** One entry of the credits: a project Noxio is built on or a library it ships. */
data class Credit(
    val name: String,
    val by: String,
    val licenses: List<CreditLicense>,
    val website: String?,
)

/** The credits, split into what Noxio is built on and the libraries it ships. */
data class Credits(val foundations: List<Credit>, val libraries: List<Credit>)

/**
 * Turns the library list that the AboutLibraries Gradle plugin writes at build time (plus the
 * entries in `config/aboutlibraries`) into the credits shown under About > Licenses.
 */
object CreditsList {
    /** What Noxio is built on, in this order, above the other libraries. */
    val FOUNDATIONS =
        listOf(
            "de.devfin:noxio",
            "org.oxycblt:auxio",
            "org.oxycblt:musikr",
            "androidx.media3:media3",
            "org.taglib:taglib",
            "org.ffmpeg:ffmpeg",
        )

    fun parse(json: String): Credits = from(Libs.Builder().withJson(json).build().libraries)

    /** AndroidX ships as dozens of parts; they are listed as one entry. */
    const val ANDROIDX_GROUP = "androidx."
    const val ANDROIDX_WEBSITE = "https://developer.android.com/jetpack/androidx"

    fun from(libraries: List<Library>): Credits {
        val credits = libraries.associate { it.uniqueId to credit(it) }
        val foundations = FOUNDATIONS.mapNotNull { credits[it] }
        val taken = foundations.map { it.name.lowercase() }.toMutableSet()
        val (androidx, others) =
            libraries
                .filter { it.uniqueId !in FOUNDATIONS }
                .partition { it.uniqueId.startsWith(ANDROIDX_GROUP) }
        val folded =
            androidx
                .map { credits.getValue(it.uniqueId) }
                .takeIf { it.isNotEmpty() }
                ?.let { parts ->
                    Credit(
                        name = "AndroidX",
                        by =
                            parts
                                .map { it.by }
                                .filter(String::isNotBlank)
                                .distinct()
                                .joinToString(", "),
                        licenses =
                            parts
                                .flatMap { it.licenses }
                                .distinctBy { it.name }
                                .sortedBy {
                                    it.name
                                },
                        website = ANDROIDX_WEBSITE,
                    )
                }
        val rest =
            (others.map { credits.getValue(it.uniqueId) } + listOfNotNull(folded))
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                // Some projects publish several artifacts under one name; list it once.
                .filter { taken.add(it.name.lowercase()) }
        return Credits(foundations, rest)
    }

    private fun credit(library: Library): Credit {
        val all =
            library.licenses.map {
                CreditLicense(it.name, it.licenseContent.orEmpty().trim(), it.url)
            }
        // A license the plugin could not find a text for is dropped when there is another one,
        // for example when config/aboutlibraries names the right license of a library.
        val withText = all.filter { it.text.isNotEmpty() }
        val licenses = (withText.ifEmpty { all }).distinctBy { it.name }.sortedBy { it.name }
        val by =
            library.developers
                .mapNotNull { it.name?.takeIf(String::isNotBlank) }
                .distinct()
                .ifEmpty { listOfNotNull(library.organization?.name) }
                .joinToString(", ")
        return Credit(
            name = library.name.ifBlank { library.uniqueId },
            by = by,
            licenses = licenses,
            website = library.website?.takeIf(String::isNotBlank) ?: library.scm?.url,
        )
    }
}
