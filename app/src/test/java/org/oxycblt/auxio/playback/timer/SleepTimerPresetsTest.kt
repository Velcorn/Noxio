/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimerPresetsTest.kt is part of Auxio.
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
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SleepTimerPresetsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val presets = SleepTimerPresets(context)
    private val key = context.getString(R.string.set_key_sleep_presets)

    @Before fun clear() = preferences.edit(commit = true) { clear() }

    @Test
    fun defaultsAndLegacyCustomValuesAreSorted() {
        assertEquals(listOf(30, 45, 60), presets.load())
        assertEquals(listOf(30, 60, 90), presets.load(90))
        assertEquals(listOf(1, 30, 60), presets.load(-1))
    }

    @Test
    fun allThreeEditedDurationsSurviveReopeningAndOverrideTheLegacyValue() {
        presets.save(listOf(120, 10, 50))
        assertEquals(listOf(10, 50, 120), SleepTimerPresets(context).load(200))
    }

    @Test
    fun savingAndRestoringValuesEnforcesTheDurationLimits() {
        presets.save(listOf(-10, 60, 1000))
        assertEquals(listOf(1, 60, 599), presets.load())
        preferences.edit(commit = true) { putString(key, "999,0,3") }
        assertEquals(listOf(1, 3, 599), presets.load())
    }

    @Test
    fun malformedBackupsFallBackToTheThreeDefaults() {
        for (stored in listOf("", "1,2", "1,2,3,4", "1,no,3", "1,999999999999,3")) {
            preferences.edit(commit = true) { putString(key, stored) }
            assertEquals(listOf(30, 45, 60), presets.load())
        }
    }

    @Test
    fun editingAnyChoiceSortsTheRowAndFollowsTheEditedChoice() {
        assertEquals(
            EditedSleepTimerPreset(listOf(45, 60, 90), 2),
            SleepTimerPresets.edit(listOf(30, 45, 60), 0, "90"),
        )
        assertEquals(
            EditedSleepTimerPreset(listOf(10, 30, 60), 0),
            SleepTimerPresets.edit(listOf(30, 45, 60), 1, "10"),
        )
        assertEquals(
            EditedSleepTimerPreset(listOf(30, 40, 45), 1),
            SleepTimerPresets.edit(listOf(30, 45, 60), 2, "40"),
        )
    }

    @Test
    fun duplicateDurationsKeepTheEditedChoiceSelected() {
        assertEquals(
            EditedSleepTimerPreset(listOf(30, 30, 45), 1),
            SleepTimerPresets.edit(listOf(30, 45, 60), 2, "30"),
        )
    }

    @Test
    fun blankOrInvalidInputDoesNotReplaceADuration() {
        for (text in listOf("", "abc", "999999999999")) {
            assertNull(SleepTimerPresets.edit(listOf(30, 45, 60), 1, text))
        }
    }

    @Test
    fun confirmedEditsAreClampedToTheSupportedRange() {
        assertEquals(
            EditedSleepTimerPreset(listOf(1, 30, 60), 0),
            SleepTimerPresets.edit(listOf(30, 45, 60), 1, "0"),
        )
        assertEquals(
            EditedSleepTimerPreset(listOf(30, 60, 599), 2),
            SleepTimerPresets.edit(listOf(30, 45, 60), 1, "999"),
        )
    }
}
