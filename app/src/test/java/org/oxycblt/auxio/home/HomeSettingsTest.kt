/*
 * Copyright (c) 2026 Auxio Project
 * HomeSettingsTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.home

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.home.tabs.Tab
import org.oxycblt.auxio.music.MusicType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val key = context.getString(org.oxycblt.auxio.R.string.set_key_home_tabs)

    @Before
    fun clearTheSettings() {
        PreferenceManager.getDefaultSharedPreferences(context).edit(commit = true) { clear() }
    }

    private fun save(code: Int) =
        PreferenceManager.getDefaultSharedPreferences(context).edit { putInt(key, code) }

    private fun types() = HomeSettingsImpl(context).also { it.migrate() }.homeTabs.map { it.type }

    private val newDefault =
        listOf(MusicType.ARTISTS, MusicType.ALBUMS, MusicType.SONGS, MusicType.PLAYLISTS)

    @Test
    fun withNothingSavedTheOrderIsArtistsAlbumsSongsPlaylists() = assertEquals(newDefault, types())

    @Test
    fun aSavedAuxioDefaultWithGenresBecomesTheNewDefault() {
        save(0b1000_1001_1010_1011_1100)
        assertEquals(newDefault, types())
    }

    @Test
    fun aSavedAuxioDefaultWithoutGenresBecomesTheNewDefault() {
        save(0b1000_1001_1010_1100_0000)
        assertEquals(newDefault, types())
    }

    @Test
    fun anOrderSetByHandIsKept() {
        save(0b1001_1000_1010_1100_0000)
        assertEquals(
            listOf(MusicType.ALBUMS, MusicType.SONGS, MusicType.ARTISTS, MusicType.PLAYLISTS),
            types(),
        )
    }

    @Test
    fun theOldOrderChosenAfterTheResetIsKept() {
        types()
        save(0b1000_1001_1010_1100_0000)
        assertEquals(
            listOf(MusicType.SONGS, MusicType.ALBUMS, MusicType.ARTISTS, MusicType.PLAYLISTS),
            types(),
        )
    }

    @Test
    fun aHiddenTabIsKeptHidden() {
        save(0b1000_1001_1010_0100_0000)
        assertEquals(
            Tab.Invisible(MusicType.PLAYLISTS),
            HomeSettingsImpl(context).also { it.migrate() }.homeTabs.last(),
        )
    }
}
