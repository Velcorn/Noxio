/*
 * Copyright (c) 2026 Auxio Project
 * ReplayGainScanner.kt is part of Auxio.
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

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/** Where the ReplayGain scan is. */
sealed interface ScanState {
    /** Nothing is running. */
    data class Idle(
        val lastMeasured: Int = 0,
        val lastFailed: Int = 0,
        val finished: Boolean = false,
    ) : ScanState

    /** [done] of [total] songs are done, [failed] of those could not be measured. */
    data class Running(val done: Int, val total: Int, val failed: Int) : ScanState
}

/**
 * Measures the loudness of the songs that have no ReplayGain tag and keeps the result in the
 * [ReplayGainStore]. Songs that were measured before are skipped, so a scan can be stopped and
 * continued.
 */
@Singleton
class ReplayGainScanner
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val store: ReplayGainStore,
) {
    private val mutableState = MutableStateFlow<ScanState>(ScanState.Idle())
    val state: StateFlow<ScanState>
        get() = mutableState

    /** The songs a scan would measure: no ReplayGain tag, and not measured yet. */
    fun pending(): List<Song> =
        musicRepository.library?.songs.orEmpty().filter {
            it.replayGainAdjustment.track == null && !store.isMeasured(it)
        }

    /**
     * Measure all [pending] songs. Returns when done, or when the calling coroutine is cancelled.
     */
    suspend fun scan() {
        if (mutableState.value is ScanState.Running) {
            return
        }
        val songs = pending()
        val done = AtomicInteger()
        val failed = AtomicInteger()
        val measured = AtomicInteger()
        mutableState.value = ScanState.Running(0, songs.size, 0)
        try {
            withContext(Dispatchers.Default) {
                val queue = ConcurrentLinkedQueue(songs)
                coroutineScope {
                    repeat(WORKERS) {
                        launch {
                            while (true) {
                                val song = queue.poll() ?: break
                                val loudness =
                                    try {
                                        LoudnessDecoder.measure(context, song.uri)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        L.w("Could not measure $song: $e")
                                        null
                                    }
                                if (loudness != null) {
                                    store.save(song, loudness.replayGainDb())
                                    measured.incrementAndGet()
                                } else {
                                    failed.incrementAndGet()
                                }
                                mutableState.value =
                                    ScanState.Running(
                                        done.incrementAndGet(),
                                        songs.size,
                                        failed.get(),
                                    )
                            }
                        }
                    }
                }
            }
            mutableState.value = ScanState.Idle(measured.get(), failed.get(), finished = true)
        } catch (e: CancellationException) {
            mutableState.value = ScanState.Idle(measured.get(), failed.get())
            throw e
        }
    }

    private companion object {
        /** Decoding is the slow part, so a few songs are measured at once. */
        val WORKERS = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
    }
}
