/*
 * Copyright (c) 2026 Auxio Project
 * LauncherIconTest.kt is part of Auxio.
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

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherIconTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun enabled(alias: String) =
        context.packageManager.getComponentEnabledSetting(
            ComponentName(context.packageName, "org.oxycblt.auxio.$alias")
        ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    @Test
    fun everyIconHasItsOwnCode() {
        assertEquals(
            LauncherIcon.entries.size,
            LauncherIcon.entries.map { it.intCode }.toSet().size,
        )
    }

    @Test
    fun aStoredCodeFindsItsIconAndAnUnknownOneNone() {
        LauncherIcon.entries.forEach { assertEquals(it, LauncherIcon.fromIntCode(it.intCode)) }
        assertNull(LauncherIcon.fromIntCode(-1))
    }

    @Test
    fun applyingAnIconEnablesItAndDisablesTheOthers() {
        LauncherIcon.apply(context, LauncherIcon.MINIMAL)
        assertTrue(enabled("MainActivityMinimal"))
        assertEquals(false, enabled("MainActivityDefault"))

        LauncherIcon.apply(context, LauncherIcon.DEFAULT)
        assertTrue(enabled("MainActivityDefault"))
        assertEquals(false, enabled("MainActivityMinimal"))
    }
}
