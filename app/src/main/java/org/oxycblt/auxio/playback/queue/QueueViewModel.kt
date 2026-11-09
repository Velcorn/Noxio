/*
 * Copyright (c) 2022 Auxio Project
 * QueueViewModel.kt is part of Auxio.
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

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.oxycblt.auxio.list.adapter.UpdateInstructions
import org.oxycblt.auxio.list.sort.Sort
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.state.QueueChange
import org.oxycblt.auxio.util.Event
import org.oxycblt.auxio.util.MutableEvent
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * A [ViewModel] that manages the current queue state and allows navigation through the queue.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@HiltViewModel
class QueueViewModel @Inject constructor(private val playbackManager: PlaybackStateManager) :
    ViewModel(), PlaybackStateManager.Listener {

    private val _queue = MutableStateFlow(listOf<Song>())
    /** The current queue. */
    val queue: StateFlow<List<Song>> = _queue
    private val _queueInstructions = MutableEvent<UpdateInstructions>()
    /** Instructions for how to update [queue] in the UI. */
    val queueInstructions: Event<UpdateInstructions> = _queueInstructions

    /** Controls whether the queue should be force-scrolled to a particular location. */
    private val _query = MutableStateFlow("")

    /** What the queue is filtered by, empty if it is not. */
    val query: StateFlow<String> = _query

    val scrollTo: Event<Int>
        field = MutableEvent<Int>()

    /** The index of the currently playing song in the queue. */
    val index: StateFlow<Int>
        field = MutableStateFlow(playbackManager.index)

    init {
        playbackManager.addListener(this)
    }

    override fun onIndexMoved(index: Int) {
        L.d("Index moved, synchronizing and scrolling to new position")
        scrollTo.put(index)
        this.index.value = index
    }

    override fun onQueueChanged(queue: List<Song>, index: Int, change: QueueChange) {
        // Queue changed trivially due to item mo -> Diff queue, stay at current index.
        L.d("Updating queue display")
        _queueInstructions.put(change.instructions)
        _queue.value = queue
        if (change.type != QueueChange.Type.MAPPING) {
            // Index changed, make sure it remains updated without actually scrolling to it.
            L.d("Index changed with queue, synchronizing new position")
            this.index.value = index
        }
    }

    override fun onQueueReordered(queue: List<Song>, index: Int, isShuffled: Boolean) {
        // Queue changed completely -> Replace queue, update index
        L.d("Queue changed completely, replacing queue and position")
        _queueInstructions.put(UpdateInstructions.Replace(0))
        scrollTo.put(index)
        _queue.value = queue
        this.index.value = index
    }

    override fun onNewPlayback(
        parent: MusicParent?,
        queue: List<Song>,
        index: Int,
        isShuffled: Boolean,
    ) {
        // Entirely new queue -> Replace queue, update index
        L.d("New playback, replacing queue and position")
        _queueInstructions.put(UpdateInstructions.Replace(0))
        scrollTo.put(index)
        _queue.value = queue
        this.index.value = index
    }

    override fun onCleared() {
        playbackManager.removeListener(this)
    }

    /**
     * Start playing the the queue item at the given index.
     *
     * @param adapterIndex The index of the queue item to play. Does nothing if the index is out of
     *   range.
     */
    /** Only show the songs of the queue that match [query] in the title, artist or album. */
    fun setQuery(query: String) {
        _query.value = query
    }

    /** The positions in the queue of the songs that are shown, all of them if not filtered. */
    fun shownIndices() = QueueOrder.filter(queue.value, query.value)

    /** The position in the queue of what is at [adapterIndex] in the list, -1 if there is none. */
    private fun queueIndex(adapterIndex: Int) =
        if (query.value.isBlank()) adapterIndex else shownIndices().getOrElse(adapterIndex) { -1 }

    /** Put the whole queue in the order of [sort]. The current song keeps playing. */
    fun sortQueue(sort: Sort) {
        val queue = queue.value
        L.d("Sorting the queue by $sort")
        playbackManager.reorderQueue(QueueOrder.matching(queue, sort.songs(queue)))
    }

    /** Put the whole queue in a random order. The current song keeps playing. */
    fun shuffleQueue() {
        L.d("Putting the queue in a random order")
        playbackManager.reorderQueue(QueueOrder.random(queue.value.size))
    }

    /** Turn the whole queue around. The current song keeps playing. */
    fun reverseQueue() {
        L.d("Reversing the queue")
        playbackManager.reorderQueue(QueueOrder.reversed(queue.value.size))
    }

    fun goto(adapterIndex: Int) {
        val index = queueIndex(adapterIndex)
        if (index !in queue.value.indices) {
            return
        }
        L.d("Going to position $index in queue")
        playbackManager.goto(index)
    }

    /**
     * Remove a queue item at the given index.
     *
     * @param adapterIndex The index of the queue item to play. Does nothing if the index is out of
     *   range.
     */
    fun removeQueueDataItem(adapterIndex: Int) {
        val index = queueIndex(adapterIndex)
        if (index !in queue.value.indices) {
            return
        }
        L.d("Removing item $index in queue")
        playbackManager.removeQueueItem(index)
    }

    /**
     * Move a queue item from one index to another index.
     *
     * @param adapterFrom The index of the queue item to move.
     * @param adapterTo The destination index for the queue item.
     * @return true if the items were moved, false otherwise.
     */
    fun moveQueueDataItems(adapterFrom: Int, adapterTo: Int): Boolean {
        // Songs cannot be moved around in a filtered list, there is no telling where they go.
        if (query.value.isNotBlank()) {
            return false
        }
        if (adapterFrom !in queue.value.indices || adapterTo !in queue.value.indices) {
            return false
        }
        L.d("Moving $adapterFrom to $adapterFrom in queue")
        playbackManager.moveQueueItem(adapterFrom, adapterTo)
        return true
    }
}
