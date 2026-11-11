/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimerPresets.kt is part of Auxio.
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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.oxycblt.auxio.R

/** The three editable duration choices, saved in ascending order. */
class SleepTimerPresets @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val key = context.getString(R.string.set_key_sleep_presets)
    private val defaults = context.resources.getIntArray(R.array.def_sleep_presets).toList()

    fun load(legacyCustomMinutes: Int? = null): List<Int> {
        val stored = preferences.getString(key, null)?.split(',')?.map { it.toIntOrNull() }
        val values =
            if (stored?.size == 3 && stored.all { it != null }) {
                stored.filterNotNull()
            } else
                defaults.mapIndexed { index, minutes ->
                    if (index == 1) legacyCustomMinutes ?: minutes else minutes
                }
        return normalize(values)
    }

    fun save(minutes: List<Int>) {
        preferences.edit { putString(key, normalize(minutes).joinToString(",")) }
    }

    companion object {
        private fun normalize(minutes: List<Int>): List<Int> {
            require(minutes.size == 3)
            return minutes
                .map { it.coerceIn(SleepTimer.MIN_MINUTES, SleepTimer.MAX_MINUTES) }
                .sorted()
        }

        /** Confirm one edit and follow that same choice when it moves in the sorted row. */
        fun edit(minutes: List<Int>, index: Int, text: String): EditedSleepTimerPreset? {
            require(minutes.size == 3 && index in minutes.indices)
            val value =
                text.toIntOrNull()?.coerceIn(SleepTimer.MIN_MINUTES, SleepTimer.MAX_MINUTES)
                    ?: return null
            val ordered =
                minutes
                    .mapIndexed { i, old -> i to if (i == index) value else old }
                    .sortedBy { it.second }
            return EditedSleepTimerPreset(
                ordered.map { it.second },
                ordered.indexOfFirst { it.first == index },
            )
        }
    }
}

data class EditedSleepTimerPreset(val minutes: List<Int>, val selectedIndex: Int)
