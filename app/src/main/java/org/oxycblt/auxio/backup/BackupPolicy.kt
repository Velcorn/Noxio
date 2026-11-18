/*
 * Copyright (c) 2026 Auxio Project
 * BackupPolicy.kt is part of Auxio.
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
import java.time.LocalDateTime
import org.oxycblt.auxio.stats.NoxioFileName

/** How much a backup holds, which is what the safeguard compares. */
data class BackupCounts(val plays: Int, val playlists: Int)

/**
 * The rules about backup files: how they are named, when a new one looks wrong, and which old ones
 * are removed.
 */
object BackupPolicy {
    /** How many backups are kept. */
    const val KEEP = 5

    /**
     * Backups are `Noxio_Backup_YYYY-MM-DD_HH-MM-SS.zip`, so several of one day each get their own
     * file. Versions before 1.24 wrote only the day (`Noxio_YYYY-MM-DD.zip`), before 1.21 with
     * `noxio_` in lower case, and the `_small` ones marked a backup the user insisted on; all still
     * count as backups, from the start of their day.
     */
    private val NAME =
        Regex(
            """[Nn]oxio_(?:Backup_)?(\d{4}-\d{2}-\d{2})(?:_(\d{2})-(\d{2})-(\d{2}))?(_small)?\.zip""",
        )

    fun fileName(time: LocalDateTime) = NoxioFileName.of(NoxioFileName.BACKUP, "zip", time)

    /** @return When the file was made, or null if it is not a backup file of Noxio. */
    fun parse(fileName: String): LocalDateTime? {
        val match = NAME.matchEntire(fileName) ?: return null
        return runCatching {
            val day = LocalDate.parse(match.groupValues[1])
            if (match.groupValues[2].isEmpty()) {
                day.atStartOfDay()
            } else {
                val (hour, minute, second) = match.groupValues.subList(2, 5).map { it.toInt() }
                day.atTime(hour, minute, second)
            }
        }
            .getOrNull()
    }

    enum class Verdict {
        OK,
        /** There are no plays and no playlists, but the biggest saved backup has some. */
        EMPTY,
        /** There are a quarter or more fewer plays than in the biggest saved backup. */
        SHRUNK,
    }

    /**
     * What a new backup is compared with: the most plays and the most playlists of the saved
     * [backups] (each with its time), counting only those from [since] on. The biggest one is used
     * and not the newest, as a newest one that already lost a little would let the next one lose a
     * little more, and so on, until the good ones are gone. [since] is the time of the smaller
     * backup the user accepted, which then is the new level.
     *
     * @return null if there is nothing to compare with.
     */
    fun baseline(
        backups: List<Pair<LocalDateTime, BackupCounts>>,
        since: LocalDateTime?,
    ): BackupCounts? {
        val counted = backups.filter { since == null || !it.first.isBefore(since) }
        if (counted.isEmpty()) return null
        return BackupCounts(
            counted.maxOf { it.second.plays },
            counted.maxOf { it.second.playlists },
        )
    }

    /**
     * Whether writing [now] could damage what is saved: a new install, a reset or a failed import
     * must not replace a good backup.
     *
     * @param baseline What the biggest saved backup holds, see [baseline], or null if there is
     *   none.
     */
    fun check(now: BackupCounts, baseline: BackupCounts?): Verdict {
        if (baseline == null) return Verdict.OK
        if (
            now.plays == 0 && now.playlists == 0 && (baseline.plays > 0 || baseline.playlists > 0)
        ) {
            return Verdict.EMPTY
        }
        if (baseline.plays >= MIN_PLAYS_TO_COMPARE && now.plays * 4 < baseline.plays * 3) {
            return Verdict.SHRUNK
        }
        return Verdict.OK
    }

    /**
     * Which of the backup files to delete: the oldest ones beyond [KEEP]. Files that are not
     * backups are never listed.
     */
    fun toDelete(fileNames: List<String>): List<String> =
        fileNames
            .mapNotNull { name -> parse(name)?.let { name to it } }
            // Newest first; of the same time the plain name is kept before an older `_small` one.
            .sortedWith(
                compareByDescending<Pair<String, LocalDateTime>> { it.second }
                    .thenBy { it.first.contains("_small") },
            )
            .drop(KEEP)
            .map { it.first }

    private const val MIN_PLAYS_TO_COMPARE = 10
}
