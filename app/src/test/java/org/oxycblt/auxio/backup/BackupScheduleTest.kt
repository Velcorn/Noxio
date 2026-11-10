/*
 * Copyright (c) 2026 Auxio Project
 * BackupScheduleTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.backup

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.oxycblt.auxio.backup.BackupSchedule.Frequency

class BackupScheduleTest {
    private val zone = ZoneId.of("Europe/Berlin")

    // Tuesday, November 10th 2026.
    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0, year: Int = 2026) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)

    private val now = at(11, 10, 13, 15)

    @Test
    fun anOffBackupHasNoNextRun() {
        assertNull(BackupSchedule(Frequency.OFF).nextRun(now))
    }

    @Test
    fun aDailyBackupRunsTodayIfItsHourIsStillAhead() {
        assertEquals(at(11, 10, 22), BackupSchedule(Frequency.DAILY, hour = 22).nextRun(now))
    }

    @Test
    fun aDailyBackupWhoseHourIsOverRunsTomorrow() {
        assertEquals(at(11, 11, 3), BackupSchedule(Frequency.DAILY, hour = 3).nextRun(now))
        // Exactly at the hour it is over, so a run does not plan itself again.
        assertEquals(
            at(11, 11, 13),
            BackupSchedule(Frequency.DAILY, hour = 13).nextRun(at(11, 10, 13)),
        )
    }

    @Test
    fun aWeeklyBackupRunsOnItsDay() {
        assertEquals(
            at(11, 16, 3),
            BackupSchedule(Frequency.WEEKLY, DayOfWeek.MONDAY, hour = 3).nextRun(now),
        )
        assertEquals(
            at(11, 13, 20),
            BackupSchedule(Frequency.WEEKLY, DayOfWeek.FRIDAY, hour = 20).nextRun(now),
        )
    }

    @Test
    fun aWeeklyBackupOnTodayRunsTodayOrInAWeek() {
        val tuesday = BackupSchedule(Frequency.WEEKLY, DayOfWeek.TUESDAY, hour = 20)
        assertEquals(at(11, 10, 20), tuesday.nextRun(now))
        assertEquals(at(11, 17, 8), tuesday.copy(hour = 8).nextRun(now))
    }

    @Test
    fun aMonthlyBackupRunsOnItsDayOfTheMonth() {
        val schedule = BackupSchedule(Frequency.MONTHLY, dayOfMonth = 15, hour = 3)
        assertEquals(at(11, 15, 3), schedule.nextRun(now))
        assertEquals(at(12, 1, 3), schedule.copy(dayOfMonth = 1).nextRun(now))
    }

    @Test
    fun aMonthlyBackupInAShortMonthRunsOnItsLastDay() {
        val schedule = BackupSchedule(Frequency.MONTHLY, dayOfMonth = 31, hour = 3)
        // November has 30 days.
        assertEquals(at(11, 30, 3), schedule.nextRun(now))
        assertEquals(at(2, 28, 3, year = 2027), schedule.nextRun(at(1, 31, 4, year = 2027)))
    }

    @Test
    fun theYearMovesOnInDecember() {
        val schedule = BackupSchedule(Frequency.MONTHLY, dayOfMonth = 1, hour = 3)
        assertEquals(at(1, 1, 3, year = 2027), schedule.nextRun(at(12, 5, 10)))
    }

    @Test
    fun theHourStaysTheSameAcrossAChangeOfTheClock() {
        // Summer time ends on October 25th 2026 in Berlin.
        val schedule = BackupSchedule(Frequency.DAILY, hour = 3)
        assertEquals(at(10, 26, 3), schedule.nextRun(at(10, 25, 12)))
    }

    @Test
    fun aStoredCodeTurnsBackIntoItsFrequency() {
        for (frequency in Frequency.entries) {
            assertEquals(frequency, Frequency.of(frequency.code))
        }
        assertEquals(Frequency.OFF, Frequency.of(42))
    }
}
