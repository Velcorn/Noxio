/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimerDurationTileTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.timer

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.ViewSleepTimerDurationBinding
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SleepTimerDurationTileTest {
    private fun checkContrast(theme: Int) {
        val context =
            ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), theme)
        val tile = ViewSleepTimerDurationBinding.inflate(LayoutInflater.from(context))
        for (checked in listOf(false, true)) {
            tile.durationButton.isChecked = checked
            val background =
                tile.durationButton.backgroundTintList!!.getColorForState(
                    tile.durationButton.drawableState,
                    0,
                )
            assertTrue(
                "Duration label must remain readable",
                ColorUtils.calculateContrast(tile.durationButton.currentTextColor, background) >=
                    4.5,
            )
            assertTrue(
                "Entered minutes must remain readable",
                ColorUtils.calculateContrast(tile.durationInput.currentTextColor, background) >=
                    4.5,
            )
        }
    }

    @Test
    @Config(qualifiers = "night")
    fun selectedAndUnselectedTilesAreReadableInDarkAndBlackThemes() {
        checkContrast(R.style.Theme_Auxio_Base)
        checkContrast(R.style.Theme_Auxio_Black)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun selectedAndUnselectedTilesAreReadableInTheLightTheme() {
        checkContrast(R.style.Theme_Auxio_Base)
    }
}
