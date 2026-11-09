/*
 * Copyright (c) 2026 Auxio Project
 * StatsWeekStartTest.kt is part of Auxio.
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
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The day a week starts on can be chosen: the setting and the ranges that follow it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsWeekStartTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val key = context.getString(R.string.set_key_stats_week_start)

    private fun at(year: Int, month: Int, day: Int) =
        ZonedDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    // Wednesday, November 15, 2023, at noon. The calendar is a US one, which starts on Sunday.
    private val wednesday = at(2023, 11, 15) + 12 * 3_600_000L

    private fun week(firstDay: Int) =
        statsRange(
            StatsPeriod.WEEK,
            0,
            wednesday,
            Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US),
            firstDay,
        )

    @Test
    fun theSettingIsMondayUntilItIsChanged() {
        preferences.edit { clear() }
        assertEquals(Calendar.MONDAY, StatsSettings(context).firstDayOfWeek)
    }

    @Test
    fun aChosenDayIsUsed() {
        for (day in listOf(Calendar.MONDAY, Calendar.SUNDAY, Calendar.SATURDAY)) {
            preferences.edit { putInt(key, day) }
            assertEquals(day, StatsSettings(context).firstDayOfWeek)
        }
    }

    @Test
    fun aValueThatIsNotADayWeOfferFallsBackToMonday() {
        preferences.edit { putInt(key, 99) }
        assertEquals(Calendar.MONDAY, StatsSettings(context).firstDayOfWeek)
    }

    @Test
    fun theWeekCanStartOnMonday() {
        val week = week(Calendar.MONDAY)
        assertEquals(at(2023, 11, 13), week.first)
        assertEquals(at(2023, 11, 20), week.last + 1)
    }

    @Test
    fun theWeekCanStartOnSunday() {
        val week = week(Calendar.SUNDAY)
        assertEquals(at(2023, 11, 12), week.first)
        assertEquals(at(2023, 11, 19), week.last + 1)
    }

    @Test
    fun theWeekCanStartOnSaturday() {
        val week = week(Calendar.SATURDAY)
        assertEquals(at(2023, 11, 11), week.first)
        assertEquals(at(2023, 11, 18), week.last + 1)
    }

    @Test
    fun everyWeekIsSevenDaysWhateverTheFirstDay() {
        for (day in listOf(Calendar.MONDAY, Calendar.SUNDAY, Calendar.SATURDAY)) {
            val week = week(day)
            assertEquals(7 * 24 * 3_600_000L, week.last + 1 - week.first)
        }
    }
}
