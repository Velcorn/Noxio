/*
 * Copyright (c) 2026 Auxio Project
 * PlayRecorder.kt is part of Auxio.
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
 
package org.oxycblt.auxio.stats

import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.state.Progression
import org.oxycblt.auxio.playback.state.QueueChange
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * Watches playback and writes a [PlayEvent] for every song that was listened to long enough. A song
 * counts after 30 seconds, or half of it for short songs. Time is the time the song was actually
 * playing, so seeking forward does not count and pausing does not either.
 */
class PlayRecorder
private constructor(
    private val context: Context,
    private val playbackManager: PlaybackStateManager,
    private val repository: StatsRepository,
) : PlaybackStateManager.Listener {
    class Factory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val playbackManager: PlaybackStateManager,
        private val repository: StatsRepository,
    ) {
        fun create() = PlayRecorder(context, playbackManager, repository)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var song: Song? = null
    private var startedAt = 0L
    private var listenedMs = 0L
    // elapsedRealtime of when the song last started playing, or -1 while paused.
    private var playingSince = -1L

    fun attach() {
        playbackManager.addListener(this)
        begin(playbackManager.currentSong)
    }

    fun release() {
        playbackManager.removeListener(this)
        // The write of the last play is launched on the scope, which is deliberately left alone so
        // it can finish.
        finish()
    }

    override fun onProgressionChanged(progression: Progression) {
        syncSong()
        val now = SystemClock.elapsedRealtime()
        if (progression.isPlaying && playingSince < 0) {
            playingSince = now
        } else if (!progression.isPlaying && playingSince >= 0) {
            listenedMs += now - playingSince
            playingSince = -1
        }
    }

    override fun onIndexMoved(index: Int) = syncSong()

    override fun onQueueChanged(queue: List<Song>, index: Int, change: QueueChange) = syncSong()

    override fun onNewPlayback(
        parent: MusicParent?,
        queue: List<Song>,
        index: Int,
        isShuffled: Boolean,
    ) = syncSong()

    override fun onSessionEnded() {
        finish()
    }

    /** Finish the tracked song and start tracking the new one if the current song changed. */
    private fun syncSong() {
        val current = playbackManager.currentSong
        if (current?.uid != song?.uid) {
            finish()
            begin(current)
        }
    }

    private fun begin(newSong: Song?) {
        song = newSong
        startedAt = System.currentTimeMillis()
        listenedMs = 0
        playingSince = if (newSong != null && playbackManager.progression.isPlaying) now() else -1
    }

    private fun finish() {
        val finished = song ?: return
        if (playingSince >= 0) {
            listenedMs += now() - playingSince
        }
        val duration = finished.durationMs
        // 30 seconds, or half the song if it is shorter than a minute.
        val threshold = if (duration > 0) min(COUNT_AFTER_MS, duration / 2) else COUNT_AFTER_MS
        if (listenedMs >= threshold) {
            L.d("Recording play of ${finished.name.raw}: $listenedMs ms")
            val event =
                PlayEvent(
                    songUid = finished.uid.toString(),
                    title = finished.name.resolve(context),
                    artists =
                        finished.artists.joinToString(PlayEvent.ARTIST_SEPARATOR) {
                            it.name.resolve(context)
                        },
                    album = finished.album.name.resolve(context),
                    startedAt = startedAt,
                    listenedMs = listenedMs,
                    durationMs = duration,
                    filePath = finished.path.components.toString(),
                    albumArtist =
                        finished.album.artists.joinToString(PlayEvent.ARTIST_SEPARATOR) {
                            it.name.resolve(context)
                        },
                    genre = finished.genres.firstOrNull()?.name?.resolve(context) ?: "",
                    year = finished.date?.year ?: 0,
                )
            scope.launch { repository.record(event) }
        }
        song = null
        listenedMs = 0
        playingSince = -1
    }

    private fun now() = SystemClock.elapsedRealtime()

    private companion object {
        const val COUNT_AFTER_MS = 30_000L
    }
}
