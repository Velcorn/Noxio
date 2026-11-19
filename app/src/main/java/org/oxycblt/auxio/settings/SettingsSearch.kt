/*
 * Copyright (c) 2026 Auxio Project
 * SettingsSearch.kt is part of Auxio.
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

import java.text.Normalizer

/**
 * Noxio: finding a setting by what it is called. Every setting is an [Entry] with the screen of
 * Settings it is on; a query matches when each of its words is found in the title or the summary,
 * ignoring case and accents. Titles that match come first, then the order of the screens.
 */
object SettingsSearch {
    data class Entry(val title: String, val summary: String, val screenKey: String)

    fun match(entries: List<Entry>, query: String): List<Entry> {
        val words = normalize(query).split(' ').filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        return entries
            .distinctBy { it.title to it.screenKey }
            .filter { entry ->
                val text = normalize("${entry.title} ${entry.summary}")
                words.all(text::contains)
            }
            .sortedBy { entry ->
                val title = normalize(entry.title)
                if (words.all(title::contains)) 0 else 1
            }
    }

    private fun normalize(text: String) =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
}
