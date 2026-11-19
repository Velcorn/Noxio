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
    val authors: List<String>,
    val licenses: List<CreditLicense>,
    val website: String?,
) {
    /** All authors, for example "Jake Wharton" or "A, B, C". */
    val by: String
        get() = authors.joinToString(", ")

    /**
     * The authors for a row: at most [max] names, and [andOthers] (which gets those names) when
     * there are more, so a long list does not fill the screen.
     */
    fun shortBy(max: Int = 2, andOthers: (String) -> String): String =
        if (authors.size <= max) by else andOthers(authors.take(max).joinToString(", "))
}

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

    /** A project that ships as many parts, listed as one entry. */
    data class Group(val name: String, val prefixes: List<String>, val website: String) {
        fun matches(uniqueId: String) = prefixes.any { uniqueId.startsWith(it) }
    }

    val GROUPS =
        listOf(
            Group(
                "AndroidX",
                listOf("androidx."),
                "https://developer.android.com/jetpack/androidx",
            ),
            Group(
                "Kotlin",
                listOf("org.jetbrains.kotlin:", "org.jetbrains.kotlinx:", "org.jetbrains:"),
                "https://kotlinlang.org",
            ),
            Group("Dagger & Hilt", listOf("com.google.dagger:"), "https://dagger.dev"),
            Group("Guava", listOf("com.google.guava:"), "https://github.com/google/guava"),
        )

    fun from(libraries: List<Library>): Credits {
        val credits = libraries.associate { it.uniqueId to credit(it) }
        val foundations = FOUNDATIONS.mapNotNull { credits[it] }
        val taken = foundations.map { it.name.lowercase() }.toMutableSet()
        val listed = libraries.filter { it.uniqueId !in FOUNDATIONS }
        val folded = GROUPS.mapNotNull { group ->
            val parts =
                listed.filter { group.matches(it.uniqueId) }.map { credits.getValue(it.uniqueId) }
            if (parts.isEmpty()) return@mapNotNull null
            Credit(
                name = group.name,
                authors = parts.flatMap { it.authors }.distinct(),
                licenses =
                    parts.flatMap { it.licenses }.distinctBy { it.name }.sortedBy { it.name },
                website = group.website,
            )
        }
        val others =
            listed
                .filter { lib -> GROUPS.none { it.matches(lib.uniqueId) } }
                .map { credits.getValue(it.uniqueId) }
        val rest =
            (others + folded)
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
        val authors =
            library.developers
                .mapNotNull { it.name?.takeIf(String::isNotBlank) }
                .distinct()
                .ifEmpty { listOfNotNull(library.organization?.name) }
        return Credit(
            name = library.name.ifBlank { library.uniqueId },
            authors = authors,
            licenses = licenses,
            website = library.website?.takeIf(String::isNotBlank) ?: library.scm?.url,
        )
    }
}
