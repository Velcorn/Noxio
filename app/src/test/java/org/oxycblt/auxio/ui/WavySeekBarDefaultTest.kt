/*
 * Copyright (c) 2026 Auxio Project
 * WavySeekBarDefaultTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.ui

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WavySeekBarDefaultTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val key = context.getString(R.string.set_key_wavy_seek_bar)

    @Before fun clear() = preferences.edit(commit = true) { clear() }

    private fun migrate() = UISettingsImpl(context).migrate()

    @Test
    fun aStoredOffIsTurnedOnOnce() {
        preferences.edit(commit = true) { putBoolean(key, false) }
        migrate()
        assertEquals(true, preferences.getBoolean(key, false))
    }

    @Test
    fun turningItOffAfterwardsSticks() {
        migrate()
        preferences.edit(commit = true) { putBoolean(key, false) }
        migrate()
        assertEquals(false, preferences.getBoolean(key, true))
    }
}
