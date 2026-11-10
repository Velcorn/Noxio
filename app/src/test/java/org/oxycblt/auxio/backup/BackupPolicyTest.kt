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
    private fun name(day: Int, small: Boolean = false) =
        BackupPolicy.fileName(LocalDate.of(2026, 11, day), small)

    @Test
    fun aBackupIsNamedAfterItsDay() {
        assertEquals("noxio_2026-11-09.zip", name(9))
        assertEquals("noxio_2026-11-09_small.zip", name(9, small = true))
    }

    @Test
    fun theNameOfABackupIsReadBack() {
        assertEquals(
            BackupPolicy.Name(LocalDate.of(2026, 11, 9), false),
            BackupPolicy.parse("noxio_2026-11-09.zip"),
        )
        assertEquals(
            BackupPolicy.Name(LocalDate.of(2026, 11, 9), true),
            BackupPolicy.parse("noxio_2026-11-09_small.zip"),
        )
    }

    @Test
    fun otherFilesAreNotBackups() {
        assertNull(BackupPolicy.parse("noxio-stats.csv"))
        assertNull(BackupPolicy.parse("noxio_2026-11-09.zip.tmp"))
        assertNull(BackupPolicy.parse("noxio_2026-13-45.zip"))
        assertNull(BackupPolicy.parse("holiday.zip"))
    }

    // --- the safeguard ---

    private val last = BackupCounts(plays = 1000, playlists = 4)

    @Test
    fun withoutAnEarlierBackupEverythingIsFine() {
        assertEquals(BackupPolicy.Verdict.OK, BackupPolicy.check(BackupCounts(0, 0), null))
    }

    @Test
    fun anEmptyAppDoesNotReplaceABackupWithContent() {
        assertEquals(BackupPolicy.Verdict.EMPTY, BackupPolicy.check(BackupCounts(0, 0), last))
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
    fun lessThanHalfThePlaysIsSuspicious() {
        assertEquals(BackupPolicy.Verdict.SHRUNK, BackupPolicy.check(BackupCounts(499, 4), last))
        assertEquals(BackupPolicy.Verdict.OK, BackupPolicy.check(BackupCounts(500, 4), last))
        assertEquals(BackupPolicy.Verdict.OK, BackupPolicy.check(BackupCounts(1200, 0), last))
    }

    @Test
    fun smallHistoriesAreNotComparedBecauseTheyChangeALot() {
        assertEquals(
            BackupPolicy.Verdict.OK,
            BackupPolicy.check(BackupCounts(2, 0), BackupCounts(9, 0)),
        )
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
    fun smallBackupsAreRemovedFirstAndNeverCountAsGood() {
        // Newest first: 3 small ones, then 5 good ones. Only the 5 newest overall are kept, but
        // good backups are held back while fewer than 3 would remain.
        val names = listOf(name(20, true), name(19, true), name(18, true)) + (1..5).map { name(it) }
        val deleted = BackupPolicy.toDelete(names)
        // Of the 8 files the 3 oldest (the good 1, 2 and 3) are beyond the limit. Deleting them
        // would leave only 2 good backups, so only one of them goes (5 good -> 3).
        assertEquals(listOf(name(1), name(2)), deleted.sorted())
        assertEquals(false, name(3) in deleted)
    }

    @Test
    fun smallBackupsBeyondTheLimitAreRemovedWithoutTouchingGoodOnes() {
        val names = (1..5).map { name(it) } + listOf(name(25, true))
        assertEquals(
            listOf(name(1)),
            BackupPolicy.toDelete(names).filter { !it.contains("_small") },
        )
    }
}
