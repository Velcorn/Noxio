/*
 * Copyright (c) 2026 Auxio Project
 * MusicSettingsTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.musikr.fs.saf.SAF
import org.oxycblt.musikr.tag.interpret.Separators
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val settings = MusicSettingsImpl(context)

    @Test
    fun automaticReloadingStaysOffEvenAfterRestoringAnEnabledPreference() {
        assertFalse(settings.shouldBeObserving)
        preferences.edit(commit = true) { putBoolean("auxio_observing", true) }
        assertFalse(settings.shouldBeObserving)
    }

    @Test
    fun oldSeparatorsCannotSplitArtistNamesButNativeMultiValueTagsRemainIntact() {
        preferences.edit(commit = true) { putString("auxio_separators", ",;/+&") }
        assertEquals("", settings.separators)
        val parser = Separators.from(settings.separators)
        val names = listOf("AC/DC", "Earth, Wind & Fire", "Florence + The Machine")
        for (name in names) assertEquals(listOf(name), parser.split(listOf(name)))
        assertEquals(names, parser.split(names))
    }

    @Test
    fun hiddenFilesAreExcludedForFreshAndRestoredPreferences() {
        assertFalse(settings.safQuery.withHidden)
        // Older versions and backups could store this as enabled.
        preferences.edit(commit = true) { putBoolean("auxio_with_hidden", true) }
        assertFalse(settings.safQuery.withHidden)
    }

    @Test
    fun savingSourcesCannotEnableHiddenFilesAndKeepsTheThreadingChoice() {
        preferences.edit(commit = true) { putBoolean("auxio_with_hidden", true) }
        settings.safQuery =
            SAF.Query(emptyList(), emptyList(), withHidden = true, multithread = false)
        assertFalse(settings.safQuery.withHidden)
        assertFalse(settings.safQuery.multithread)
        assertFalse(preferences.contains("auxio_with_hidden"))
        settings.safQuery = settings.safQuery.copy(multithread = true)
        assertFalse(settings.safQuery.withHidden)
        assertTrue(settings.safQuery.multithread)
    }
}
