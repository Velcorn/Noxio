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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
 *
 * - A play is saved as soon as it counts and then kept up to date every [CHECKPOINT_MS], so killing
 *   the app halfway does not lose it.
 * - A song that starts over (repeating a single song) is a new play each time.
 *
 * The counting is done by [PlayTracker] and the saving by [PlayWrite], which are tested. This class
 * only connects them to the player.
 */
class PlayRecorder
internal constructor(
    private val playbackManager: PlaybackStateManager,
    private val repository: StatsRepository,
    private val eventFor: (Song, startedAt: Long) -> PlayEvent,
    private val keyOf: (Song) -> String,
    // The tracker is only touched on the main thread, the database work happens on one background
    // thread so the writes of a play stay in order.
    private val mainScope: CoroutineScope,
    private val writeScope: CoroutineScope,
) : PlaybackStateManager.Listener {
    class Factory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val playbackManager: PlaybackStateManager,
        private val repository: StatsRepository,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        fun create() =
            PlayRecorder(
                playbackManager,
                repository,
                eventFor = { song, startedAt -> playEventFor(context, song, startedAt) },
                keyOf = { it.uid.toString() },
                mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1)),
            )
    }

    private val tracker = PlayTracker()
    private var song: Song? = null
    private var write: PlayWrite? = null
    private var checkpointJob: Job? = null

    fun attach() {
        playbackManager.addListener(this)
        begin(playbackManager.currentSong)
    }

    fun release() {
        playbackManager.removeListener(this)
        // The last write is launched on the scope, which is deliberately left alone so it can
        // finish.
        finish()
    }

    override fun onProgressionChanged(progression: Progression) {
        syncSong()
        val now = now()
        if (tracker.isLoop(progression.calculateElapsedPositionMs(), now)) {
            // The same song started over: the play before it is done, this is a new one.
            val repeated = song
            finish()
            begin(repeated)
        }
        tracker.setPlaying(progression.isPlaying, now)
        updateCheckpoints(progression.isPlaying)
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
        if (current?.let(keyOf) != tracker.current) {
            finish()
            begin(current)
        }
    }

    private fun begin(newSong: Song?) {
        song = newSong
        val playing = newSong != null && playbackManager.progression.isPlaying
        val startedAt = System.currentTimeMillis()
        tracker.begin(
            key = newSong?.let(keyOf),
            durationMs = newSong?.durationMs ?: 0,
            wallNowMs = startedAt,
            playing = playing,
            nowMs = now(),
        )
        write = newSong?.let { PlayWrite(repository) { eventFor(it, startedAt) } }
        updateCheckpoints(playing)
    }

    /** Save the play that is ending, if it counts, and stop tracking. */
    private fun finish() {
        checkpoint()
        checkpointJob?.cancel()
        checkpointJob = null
        tracker.clear()
        song = null
        write = null
    }

    /** Save the play so far, if it already counts. */
    private fun checkpoint() {
        val play = tracker.snapshot(now()) ?: return
        val pending = write ?: return
        L.d("Saving play: ${play.listenedMs} ms")
        writeScope.launch { pending.write(play.listenedMs) }
    }

    /** Save every so often while playing, and once when paused. */
    private fun updateCheckpoints(playing: Boolean) {
        if (playing && checkpointJob == null && song != null) {
            checkpointJob = mainScope.launch {
                while (isActive) {
                    delay(CHECKPOINT_MS)
                    checkpoint()
                }
            }
        } else if (!playing && checkpointJob != null) {
            checkpointJob?.cancel()
            checkpointJob = null
            checkpoint()
        }
    }

    private fun now() = SystemClock.elapsedRealtime()

    private companion object {
        /** How often a play that is going on is saved again. */
        const val CHECKPOINT_MS = 15_000L
    }
}

/** Describe a song as the play event that is saved for it, with the names as they are shown. */
fun playEventFor(context: Context, song: Song, startedAt: Long): PlayEvent =
    PlayEvent(
        songUid = song.uid.toString(),
        title = song.name.resolve(context),
        artists =
            song.artists.joinToString(PlayEvent.ARTIST_SEPARATOR) { it.name.resolve(context) },
        album = song.album.name.resolve(context),
        startedAt = startedAt,
        listenedMs = 0,
        durationMs = song.durationMs,
        filePath = song.path.components.toString(),
        albumArtist =
            song.album.artists.joinToString(PlayEvent.ARTIST_SEPARATOR) {
                it.name.resolve(context)
            },
        genre = song.genres.firstOrNull()?.name?.resolve(context) ?: "",
        year = song.date?.year ?: 0,
    )
