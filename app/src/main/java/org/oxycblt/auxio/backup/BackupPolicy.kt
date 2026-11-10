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

/** How much a backup holds, which is what the safeguard compares. */
data class BackupCounts(val plays: Int, val playlists: Int)

/**
 * The rules about backup files: how they are named, when a new one looks wrong, and which old ones
 * are removed.
 */
object BackupPolicy {
    /** How many backups are kept. */
    const val KEEP = 5

    /** Old backups are not removed while that would leave fewer good ones than this. */
    const val MIN_GOOD = 3

    private val NAME = Regex("""noxio_(\d{4}-\d{2}-\d{2})(_small)?\.zip""")

    /**
     * @param small True for a backup that holds much less than the one before, which was written
     *   because the user asked for it. It never counts as a good backup.
     */
    fun fileName(date: LocalDate, small: Boolean = false) =
        "noxio_$date${if (small) "_small" else ""}.zip"

    data class Name(val date: LocalDate, val small: Boolean)

    /** @return What the name of a file says, or null if it is not a backup file of Noxio. */
    fun parse(fileName: String): Name? {
        val match = NAME.matchEntire(fileName) ?: return null
        val date = runCatching { LocalDate.parse(match.groupValues[1]) }.getOrNull() ?: return null
        return Name(date, match.groupValues[2].isNotEmpty())
    }

    enum class Verdict {
        OK,
        /** There are no plays and no playlists, but the last backup had some. */
        EMPTY,
        /** There are less than half of the plays of the last backup. */
        SHRUNK,
    }

    /**
     * Whether writing [now] could damage what is saved: a new install, a reset or a failed import
     * must not replace a good backup.
     *
     * @param last What the newest good backup holds, or null if there is none.
     */
    fun check(now: BackupCounts, last: BackupCounts?): Verdict {
        if (last == null) return Verdict.OK
        if (now.plays == 0 && now.playlists == 0 && (last.plays > 0 || last.playlists > 0)) {
            return Verdict.EMPTY
        }
        if (last.plays >= MIN_PLAYS_TO_COMPARE && now.plays * 2 < last.plays) {
            return Verdict.SHRUNK
        }
        return Verdict.OK
    }

    /**
     * Which of the backup files to delete: the oldest ones beyond [KEEP], as long as at least
     * [MIN_GOOD] good ones are left. Files that are not backups are never listed.
     */
    fun toDelete(fileNames: List<String>): List<String> {
        val backups =
            fileNames
                .mapNotNull { name -> parse(name)?.let { name to it } }
                // Newest first; a good file comes before a small one of the same day.
                .sortedWith(
                    compareByDescending<Pair<String, Name>> { it.second.date }
                        .thenBy { it.second.small }
                )
        var good = backups.count { !it.second.small }
        val delete = mutableListOf<String>()
        for ((name, parsed) in backups.drop(KEEP).reversed()) {
            if (!parsed.small) {
                if (good <= MIN_GOOD) continue
                good--
            }
            delete += name
        }
        return delete
    }

    private const val MIN_PLAYS_TO_COMPARE = 10
}
