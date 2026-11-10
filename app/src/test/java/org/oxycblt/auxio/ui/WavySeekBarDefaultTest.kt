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
import android.view.ContextThemeWrapper
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.slider.WavySlider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.R
import org.oxycblt.auxio.image.CoverMode
import org.oxycblt.auxio.image.ImageSettingsImpl
import org.oxycblt.auxio.playback.ui.StyledSeekBar
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WavySeekBarDefaultTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val obsoleteKeys =
        listOf(
            "auxio_round_covers",
            "noxio_wavy_seek_bar",
            "noxio_wavy_default_on",
            "auxio_square_covers",
        )

    @Before fun clear() = preferences.edit(commit = true) { clear() }

    @Test
    fun roundModeAndNaturalCoverProportionsAreFixedForFreshAndRestoredSettings() {
        val ui = UISettingsImpl(context)
        val images = ImageSettingsImpl(context)
        assertTrue(ui.roundMode)
        assertFalse(images.forceSquareCovers)

        // Restoring an older backup must not bring back the removed appearance choices.
        preferences.edit(commit = true) {
            putBoolean("auxio_round_covers", false)
            putBoolean("auxio_square_covers", true)
        }
        assertTrue(ui.roundMode)
        assertFalse(images.forceSquareCovers)
    }

    @Test
    fun migrationDiscardsRemovedSwitchesAndPreservesTheme() {
        val coverModeKey = "auxio_cover_mode2"
        val blackThemeKey = context.getString(R.string.set_key_black_theme)
        preferences.edit(commit = true) {
            obsoleteKeys.forEach { putBoolean(it, true) }
            putInt(coverModeKey, CoverMode.HIGH_QUALITY.intCode)
            putBoolean(blackThemeKey, true)
        }
        repeat(2) {
            val ui = UISettingsImpl(context)
            val images = ImageSettingsImpl(context)
            ui.migrate()
            images.migrate()
            obsoleteKeys.forEach { assertFalse(it, preferences.contains(it)) }
            assertTrue(ui.roundMode)
            assertFalse(images.forceSquareCovers)
            assertTrue(ui.useBlackTheme)
            assertEquals(CoverMode.BALANCED, images.coverMode)
            assertFalse(preferences.contains(coverModeKey))
        }
    }

    @Test
    fun balancedQualityIgnoresAllStoredModesIncludingRestoredOff() {
        val images = ImageSettingsImpl(context)
        assertEquals(CoverMode.BALANCED, images.coverMode)
        for (mode in CoverMode.entries) {
            preferences.edit(commit = true) {
                putInt("auxio_cover_mode2", mode.intCode)
                putInt("auxio_cover_mode", mode.intCode)
                putBoolean("KEY_SHOW_COVERS", false)
                putBoolean("KEY_QUALITY_COVERS", false)
            }
            assertEquals(CoverMode.BALANCED, images.coverMode)
            images.migrate()
            assertEquals(CoverMode.BALANCED, images.coverMode)
            for (key in
                listOf(
                    "auxio_cover_mode2",
                    "auxio_cover_mode",
                    "KEY_SHOW_COVERS",
                    "KEY_QUALITY_COVERS",
                )) {
                assertFalse(key, preferences.contains(key))
            }
        }
    }

    @Test
    fun waveFollowsPlaybackEvenWhenAnOldBackupDisablesIt() {
        preferences.edit(commit = true) { putBoolean("noxio_wavy_seek_bar", false) }
        val themed = ContextThemeWrapper(context, R.style.Theme_Auxio_Black)
        val seekBar = StyledSeekBar(themed)
        val slider = seekBar.findViewById<WavySlider>(R.id.seek_bar_slider)
        val waveEnabled =
            WavySlider::class.java.getDeclaredField("waveEnabled").apply {
                isAccessible = true
            }
        seekBar.setWaveEnabled(true)
        assertTrue(waveEnabled.getBoolean(slider))
        seekBar.setWaveEnabled(false)
        assertFalse(waveEnabled.getBoolean(slider))
        seekBar.setWaveEnabled(true)
        assertTrue(waveEnabled.getBoolean(slider))
    }
}
