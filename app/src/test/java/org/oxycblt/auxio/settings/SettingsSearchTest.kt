/*
 * Copyright (c) 2026 Auxio Project
 * SettingsSearchTest.kt is part of Auxio.
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
import org.junit.Test

class SettingsSearchTest {
    private val theme = SettingsSearch.Entry("Theme", "Light, dark or automatic", "ui")
    private val shuffle = SettingsSearch.Entry("Remember shuffle", "Keep shuffle on", "audio")
    private val replay = SettingsSearch.Entry("ReplayGain", "Volume of songs", "audio")
    private val backup =
        SettingsSearch.Entry("Automatic backup", "Daily, weekly or monthly", "backup")
    private val all = listOf(theme, shuffle, replay, backup)

    private fun titles(query: String) = SettingsSearch.match(all, query).map { it.title }

    @Test
    fun nothingTypedFindsNothing() {
        assertEquals(emptyList<String>(), titles("  "))
    }

    @Test
    fun titleAndSummaryAreSearchedIgnoringCase() {
        assertEquals(listOf("ReplayGain"), titles("replaygain"))
        assertEquals(listOf("ReplayGain"), titles("VOLUME"))
    }

    @Test
    fun everyWordHasToBeFoundAnywhere() {
        assertEquals(listOf("Remember shuffle"), titles("shuffle keep"))
        assertEquals(emptyList<String>(), titles("shuffle dark"))
    }

    @Test
    fun aMatchInTheTitleComesBeforeOneInTheSummary() {
        // "automatic" is in the summary of the theme and in the title of the backup.
        assertEquals(listOf("Automatic backup", "Theme"), titles("automatic"))
    }

    @Test
    fun accentsDoNotMatter() {
        val german = SettingsSearch.Entry("Hörstatistik", "", "stats")
        assertEquals(listOf(german), SettingsSearch.match(listOf(german), "horstat"))
    }

    @Test
    fun aSettingListedTwiceOnOneScreenIsShownOnce() {
        assertEquals(1, SettingsSearch.match(listOf(theme, theme), "theme").size)
    }
}
