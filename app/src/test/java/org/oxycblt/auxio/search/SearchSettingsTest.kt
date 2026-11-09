/*
 * Copyright (c) 2026 Auxio Project
 * SearchSettingsTest.kt is part of Auxio.
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

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.music.MusicType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = SearchSettingsImpl(context)

    @Test fun nothingIsPickedAtFirst() = assertEquals(emptySet<MusicType>(), settings.filters)

    @Test
    fun everyCombinationOfTypesIsKept() {
        val all = MusicType.entries
        for (mask in 0 until (1 shl all.size)) {
            val picked = all.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
            settings.filters = picked
            assertEquals(picked, SearchSettingsImpl(context).filters)
        }
    }

    @Test
    fun theOldSingleFilterBecomesTheOnlyPickedType() {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putInt("KEY_SEARCH_FILTER", MusicType.ALBUMS.intCode)
        }
        settings.migrate()
        assertEquals(setOf(MusicType.ALBUMS), settings.filters)
    }

    @Test
    fun withoutAnOldFilterTheMigrationPicksNothing() {
        settings.migrate()
        assertEquals(emptySet<MusicType>(), settings.filters)
    }
}
