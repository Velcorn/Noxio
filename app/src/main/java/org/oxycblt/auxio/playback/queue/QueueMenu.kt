/*
 * Copyright (c) 2026 Auxio Project
 * QueueMenu.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.queue

import android.view.View
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.ui.popupMenu

/**
 * Noxio: what can be done with the whole queue, from a long press on its search button (or Ctrl+M
 * in the keyboard phone mode): shuffle it, save it as a playlist or clear it.
 */
fun showQueueMenu(
    anchor: View,
    queueModel: QueueViewModel,
    musicModel: MusicViewModel,
    playbackModel: PlaybackViewModel,
) {
    popupMenu(anchor.context, anchor).apply {
        inflate(R.menu.queue_overflow)
        val hasSongs = queueModel.queue.value.isNotEmpty()
        menu.findItem(R.id.action_queue_save).isEnabled = hasSongs
        menu.findItem(R.id.action_queue_clear).isEnabled = hasSongs
        setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_queue_shuffle -> queueModel.shuffleQueue()
                // The dialog of the playlists, with a new one at its end.
                R.id.action_queue_save -> musicModel.addToPlaylist(queueModel.queue.value)
                R.id.action_queue_clear -> playbackModel.clearQueue()
            }
            true
        }
        show()
    }
}
