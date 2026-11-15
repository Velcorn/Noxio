/*
 * Copyright (c) 2026 Auxio Project
 * ReplayGainTagWriter.kt is part of Auxio.
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
 
package org.oxycblt.auxio.replaygain

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.playback.PlaybackSettings
import org.oxycblt.auxio.tags.ReplayGainTags
import org.oxycblt.auxio.tags.TagEditor
import org.oxycblt.auxio.tags.TagWriteResult
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/** Where writing the measured ReplayGain into the files is. */
sealed interface TagWriteState {
    data class Idle(val written: Int = 0, val failed: Int = 0, val finished: Boolean = false) :
        TagWriteState

    data class Running(val done: Int, val total: Int) : TagWriteState
}

/** One file to write: the song and its fields. */
data class TagWrite(val song: Song, val fields: Map<String, List<String>>)

/**
 * Noxio: writes the measured ReplayGain into the tags of the songs that use it. [plan] works out
 * what to write, the user allows it, and [ReplayGainService] runs [write].
 */
@Singleton
class ReplayGainTagWriter
@Inject
constructor(
    private val musicRepository: MusicRepository,
    private val store: ReplayGainStore,
    private val playbackSettings: PlaybackSettings,
    private val tagEditor: TagEditor,
) {
    private val mutableState = MutableStateFlow<TagWriteState>(TagWriteState.Idle())
    val state: StateFlow<TagWriteState>
        get() = mutableState

    /** The files the next [write] writes, set by [plan]. */
    @Volatile
    var planned: List<TagWrite> = emptyList()
        private set

    /** Work out which songs get which values, see [ReplayGainTags.fields]. */
    suspend fun plan(): List<TagWrite> {
        val rows = store.all()
        val preferTags = playbackSettings.replayGainPreferTags
        val writes =
            musicRepository.library?.songs.orEmpty().mapNotNull { song ->
                val fields =
                    ReplayGainTags.fields(
                        trackTag = song.replayGainAdjustment.track,
                        albumTag = song.replayGainAdjustment.album,
                        measuredTrack = store.gainFor(song),
                        measuredAlbum = store.albumGainFor(song.album),
                        trackPeak = rows[song.uid.toString()]?.peak,
                        albumPeak = store.albumPeakFor(song.album),
                        preferTags = preferTags,
                    )
                if (fields.isEmpty()) null else TagWrite(song, fields)
            }
        planned = writes
        return writes
    }

    /** Write the [planned] files one after the other. Returns when done or cancelled. */
    suspend fun write() {
        if (mutableState.value is TagWriteState.Running) return
        val writes = planned
        var written = 0
        var failed = 0
        mutableState.value = TagWriteState.Running(0, writes.size)
        try {
            for ((i, write) in writes.withIndex()) {
                when (tagEditor.write(write.song, write.fields)) {
                    TagWriteResult.Saved -> written++
                    else -> {
                        L.w("Could not write ReplayGain to ${write.song}")
                        failed++
                    }
                }
                mutableState.value = TagWriteState.Running(i + 1, writes.size)
            }
        } catch (e: CancellationException) {
            mutableState.value = TagWriteState.Idle(written, failed)
            throw e
        } finally {
            planned = emptyList()
        }
        // The library reads the changed files again, so the tags show up.
        if (written > 0) tagEditor.reloadAfter(writes.map { it.song.uri })
        mutableState.value = TagWriteState.Idle(written, failed, finished = true)
    }
}
