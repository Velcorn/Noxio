/*
 * Copyright (c) 2026 Auxio Project
 * BackupPolicyTest.kt is part of Auxio.
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

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupPolicyTest {
    private fun day(day: Int) = LocalDate.of(2026, 11, day)

    private fun name(day: Int) = BackupPolicy.fileName(day(day))

    @Test
    fun aBackupIsNamedAfterItsDay() {
        assertEquals("Noxio_2026-11-09.zip", name(9))
    }

    @Test
    fun theNameOfABackupIsReadBack() {
        assertEquals(day(9), BackupPolicy.parse("Noxio_2026-11-09.zip"))
        // Versions before 1.21 wrote the name in lower case.
        assertEquals(day(9), BackupPolicy.parse("noxio_2026-11-09.zip"))
        // Older versions marked a backup the user insisted on, it is a backup like any other now.
        assertEquals(day(9), BackupPolicy.parse("noxio_2026-11-09_small.zip"))
    }

    @Test
    fun otherFilesAreNotBackups() {
        assertNull(BackupPolicy.parse("noxio-stats.csv"))
        assertNull(BackupPolicy.parse("noxio_2026-11-09.zip.tmp"))
        assertNull(BackupPolicy.parse("noxio_2026-13-45.zip"))
        assertNull(BackupPolicy.parse("holiday.zip"))
        assertNull(BackupPolicy.parse("NOXIO_2026-11-09.zip"))
    }

    // --- the safeguard ---

    private val biggest = BackupCounts(plays = 1000, playlists = 4)

    @Test
    fun withoutAnEarlierBackupEverythingIsFine() {
        assertEquals(BackupPolicy.Verdict.OK, BackupPolicy.check(BackupCounts(0, 0), null))
    }

    @Test
    fun anEmptyAppDoesNotReplaceABackupWithContent() {
        assertEquals(BackupPolicy.Verdict.EMPTY, BackupPolicy.check(BackupCounts(0, 0), biggest))
        assertEquals(
            BackupPolicy.Verdict.EMPTY,
            BackupPolicy.check(BackupCounts(0, 0), BackupCounts(0, 1)),
        )
    }

    @Test
    fun anEmptyAppAfterAnEmptyBackupIsFine() {
        assertEquals(
            BackupPolicy.Verdict.OK,
            BackupPolicy.check(BackupCounts(0, 0), BackupCounts(0, 0)),
        )
    }

    @Test
    fun aQuarterFewerPlaysOrMoreIsSuspicious() {
        // Plays only grow, so a loss of a quarter is no normal change.
        assertEquals(BackupPolicy.Verdict.SHRUNK, BackupPolicy.check(BackupCounts(749, 4), biggest))
        assertEquals(BackupPolicy.Verdict.OK, BackupPolicy.check(BackupCounts(750, 4), biggest))
        assertEquals(BackupPolicy.Verdict.OK, BackupPolicy.check(BackupCounts(1200, 0), biggest))
    }

    @Test
    fun smallHistoriesAreNotComparedBecauseTheyChangeALot() {
        assertEquals(
            BackupPolicy.Verdict.OK,
            BackupPolicy.check(BackupCounts(2, 0), BackupCounts(9, 0)),
        )
    }

    // --- what a new backup is compared with ---

    @Test
    fun nothingSavedMeansNothingToCompareWith() {
        assertNull(BackupPolicy.baseline(emptyList(), null))
    }

    @Test
    fun theBiggestOfTheSavedBackupsIsTheLevelAndNotTheNewest() {
        val saved =
            listOf(
                day(2) to BackupCounts(1000, 4),
                day(9) to BackupCounts(800, 6),
                day(16) to BackupCounts(760, 2),
            )
        assertEquals(BackupCounts(1000, 6), BackupPolicy.baseline(saved, null))
    }

    @Test
    fun aSlowDeclineIsCaughtAgainstTheBiggestAndNotStepByStep() {
        // 1000 -> 800 -> 640 -> 512: every step on its own loses less than a quarter, but 640 is
        // already far under the 1000 that is still saved, so it is stopped there.
        var saved = listOf(day(1) to BackupCounts(1000, 0))
        var stopped = 0
        for ((i, plays) in listOf(800, 640, 512).withIndex()) {
            val now = BackupCounts(plays, 0)
            if (
                BackupPolicy.check(now, BackupPolicy.baseline(saved, null)) ==
                    BackupPolicy.Verdict.OK
            ) {
                saved = saved + (day(2 + i) to now)
            } else {
                stopped++
            }
        }
        // 800 passes (80%), 640 and 512 are under 75% of 1000.
        assertEquals(2, stopped)
        assertEquals(listOf(BackupCounts(1000, 0), BackupCounts(800, 0)), saved.map { it.second })
    }

    @Test
    fun anAcceptedSmallerLevelIsTheNewOneFromItsDayOn() {
        val saved = listOf(day(2) to BackupCounts(1000, 4), day(16) to BackupCounts(100, 1))
        assertEquals(BackupCounts(100, 1), BackupPolicy.baseline(saved, day(16)))
        // Backups of the day itself count too, and nothing from before.
        assertEquals(BackupCounts(1000, 4), BackupPolicy.baseline(saved, day(2)))
        assertNull(BackupPolicy.baseline(saved, day(20)))
    }

    // --- which files are removed ---

    @Test
    fun nothingIsRemovedUpToTheLimit() {
        assertEquals(emptyList<String>(), BackupPolicy.toDelete((1..5).map { name(it) }))
    }

    @Test
    fun theOldestBackupsBeyondTheLimitAreRemoved() {
        val names = (1..8).map { name(it) }
        assertEquals(listOf(name(1), name(2), name(3)), BackupPolicy.toDelete(names).sorted())
    }

    @Test
    fun filesThatAreNotBackupsAreNeverRemoved() {
        val names = (1..8).map { name(it) } + listOf("noxio-stats.csv", "photo.jpg")
        assertEquals(3, BackupPolicy.toDelete(names).size)
        assertEquals(false, "noxio-stats.csv" in BackupPolicy.toDelete(names))
    }

    @Test
    fun aNewBackupGetsTheCapitalizedName() {
        assertEquals("Noxio_2026-11-09.zip", BackupPolicy.nameFor(day(9), listOf(name(8))))
        assertEquals("Noxio_2026-11-09.zip", BackupPolicy.nameFor(day(9), listOf(name(9))))
    }

    @Test
    fun aSameDayBackupOfAnOlderVersionKeepsItsName() {
        val old = listOf("noxio_2026-11-08.zip", "noxio_2026-11-09.zip")
        assertEquals("noxio_2026-11-09.zip", BackupPolicy.nameFor(day(9), old))
    }

    @Test
    fun lowerAndUpperCaseBackupsAreKeptByDayAlike() {
        val names = listOf("noxio_2026-11-03.zip", "noxio_2026-11-04.zip") + (5..8).map { name(it) }
        assertEquals(listOf("noxio_2026-11-03.zip"), BackupPolicy.toDelete(names))
    }

    @Test
    fun olderSmallFilesCountLikeAnyOther() {
        val names = (3..7).map { name(it) } + listOf("noxio_2026-11-01_small.zip")
        assertEquals(listOf("noxio_2026-11-01_small.zip"), BackupPolicy.toDelete(names))
    }
}
