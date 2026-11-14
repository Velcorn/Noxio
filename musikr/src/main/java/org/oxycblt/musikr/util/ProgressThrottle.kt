/*
 * Copyright (c) 2026 Auxio Project
 * ProgressThrottle.kt is part of Auxio.
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
 
package org.oxycblt.musikr.util

/**
 * Lets a progress report through at most once per interval. Loading a library reports progress for
 * every song, and every report is a trip to the main thread, which made the main thread the slowest
 * part of loading. Not thread-safe: use one per loop that reports.
 *
 * @param intervalMs The shortest time between two reports, in milliseconds.
 * @param nanoTime The clock, in nanoseconds. Only differences are used.
 */
internal class ProgressThrottle(
    intervalMs: Long,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val intervalNs = intervalMs * 1_000_000L
    private var last = nanoTime()

    /** @return Whether a report should be made now. The call counts as a report if it is. */
    fun allow(): Boolean {
        val now = nanoTime()
        if (now - last < intervalNs) {
            return false
        }
        last = now
        return true
    }
}
