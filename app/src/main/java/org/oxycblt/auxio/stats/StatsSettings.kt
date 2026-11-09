/*
 * Copyright (c) 2026 Auxio Project
 * StatsSettings.kt is part of Auxio.
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
 
package org.oxycblt.auxio.stats

import android.content.Context
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import org.oxycblt.auxio.R

/** The user's settings for the listening stats. */
@Singleton
class StatsSettings @Inject constructor(@ApplicationContext private val context: Context) {
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    /**
     * The first day of the week, as a [Calendar] day. Monday unless the user picked another day in
     * the settings. Anything else that was stored is ignored.
     */
    val firstDayOfWeek: Int
        get() {
            val stored =
                sharedPreferences.getInt(
                    context.getString(R.string.set_key_stats_week_start),
                    Calendar.MONDAY,
                )
            return if (stored in ALLOWED_FIRST_DAYS) stored else Calendar.MONDAY
        }

    private companion object {
        val ALLOWED_FIRST_DAYS = setOf(Calendar.MONDAY, Calendar.SUNDAY, Calendar.SATURDAY)
    }
}
