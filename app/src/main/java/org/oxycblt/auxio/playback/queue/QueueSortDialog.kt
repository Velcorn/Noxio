/*
 * Copyright (c) 2026 Auxio Project
 * QueueSortDialog.kt is part of Auxio.
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

import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.list.sort.Sort
import org.oxycblt.auxio.list.sort.SortDialog

/**
 * A [SortDialog] that puts the whole queue in a new order. The current song keeps playing.
 *
 * @author Noxio
 */
@AndroidEntryPoint
class QueueSortDialog : SortDialog() {
    private val queueModel: QueueViewModel by activityViewModels()

    // The queue has no sort of its own, it is only put in an order once.
    override fun getInitialSort() = null

    override fun applyChosenSort(sort: Sort) {
        queueModel.sortQueue(sort)
    }

    override fun getModeChoices() =
        listOf(
            Sort.Mode.ByName,
            Sort.Mode.ByArtist,
            Sort.Mode.ByAlbum,
            Sort.Mode.ByDate,
            Sort.Mode.ByDuration,
            Sort.Mode.ByRandom,
        )
}
