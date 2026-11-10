/*
 * Copyright (c) 2026 Auxio Project
 * BackupSchedule.kt is part of Auxio.
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * When the automatic backup runs.
 *
 * @param weekday The day of a weekly backup.
 * @param dayOfMonth The day of a monthly backup. A month that is shorter has it on its last day.
 * @param hour The hour of the day it runs at, 0 to 23.
 */
data class BackupSchedule(
    val frequency: Frequency,
    val weekday: DayOfWeek = DayOfWeek.MONDAY,
    val dayOfMonth: Int = 1,
    val hour: Int = DEFAULT_HOUR,
) {
    /** How often the backup runs. The codes are what the settings store. */
    enum class Frequency(val code: Int) {
        OFF(0),
        DAILY(1),
        WEEKLY(2),
        MONTHLY(3);

        companion object {
            fun of(code: Int) = entries.find { it.code == code } ?: OFF
        }
    }

    /**
     * The first time after [now] that the backup is due.
     *
     * @return Null if the backup is off.
     */
    fun nextRun(now: ZonedDateTime): ZonedDateTime? {
        val time = LocalTime.of(hour.coerceIn(0, 23), 0)
        fun at(date: LocalDate) = ZonedDateTime.of(date, time, now.zone)
        val today = now.toLocalDate()
        return when (frequency) {
            Frequency.OFF -> null
            Frequency.DAILY -> at(today).takeIf { it.isAfter(now) } ?: at(today.plusDays(1))
            Frequency.WEEKLY -> {
                val date = today.with(TemporalAdjusters.nextOrSame(weekday))
                at(date).takeIf { it.isAfter(now) } ?: at(date.plusWeeks(1))
            }
            Frequency.MONTHLY -> {
                fun inMonth(first: LocalDate) =
                    at(first.withDayOfMonth(dayOfMonth.coerceIn(1, first.lengthOfMonth())))
                val first = today.withDayOfMonth(1)
                inMonth(first).takeIf { it.isAfter(now) } ?: inMonth(first.plusMonths(1))
            }
        }
    }

    companion object {
        /** Backups run at night by default, when the phone is usually idle and charging. */
        const val DEFAULT_HOUR = 3
    }
}
