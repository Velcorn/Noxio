/*
 * Copyright (c) 2026 Auxio Project
 * AutoSaver.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Calls [save] every [intervalMs] while something is playing, so the position of a long track (an
 * audiobook, a mix) is not lost if the app is stopped without notice.
 *
 * @param scope The [CoroutineScope] the timer runs in.
 * @param intervalMs The time between two saves.
 * @param save What saves the position.
 */
class AutoSaver(
    private val scope: CoroutineScope,
    private val intervalMs: Long,
    private val save: () -> Unit,
) {
    private var job: Job? = null

    /** Whether the timer runs. */
    val running: Boolean
        get() = job?.isActive == true

    /** Start the timer when [playing] begins, stop it when it ends. */
    fun setPlaying(playing: Boolean) {
        if (!playing) {
            job?.cancel()
            job = null
        } else if (!running) {
            job = scope.launch {
                while (isActive) {
                    delay(intervalMs)
                    save()
                }
            }
        }
    }
}
