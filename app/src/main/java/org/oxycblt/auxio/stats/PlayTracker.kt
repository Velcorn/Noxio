/*
 * Copyright (c) 2026 Auxio Project
 * PlayTracker.kt is part of Auxio.
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

import kotlin.math.min
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the time one song has really been playing, and decides when that counts as a play. Time is
 * only counted while playing, so pausing and seeking forward do not add to it. It does not know
 * about Android: the caller passes in the clocks, which makes it easy to test.
 *
 * Two clocks are used: the wall clock for when a play started, and a clock that only moves forward
 * for measuring how long it ran.
 */
class PlayTracker {
    /** A play that has been listened to long enough to count. */
    data class Play(val startedAt: Long, val listenedMs: Long)

    private var key: String? = null
    private var durationMs = 0L
    private var startedAt = 0L
    private var listenedMs = 0L
    private var playingSince = NOT_PLAYING

    /** The song being tracked, or null if there is none. */
    val current: String?
        get() = key

    /**
     * Start tracking a song, forgetting the one before.
     *
     * @param key Identifies the song. Null if nothing is playing.
     * @param durationMs The length of the song, 0 if unknown.
     * @param wallNowMs The time of day, for when the play started.
     * @param playing Whether the song is playing right now.
     * @param nowMs The steadily increasing clock.
     */
    fun begin(key: String?, durationMs: Long, wallNowMs: Long, playing: Boolean, nowMs: Long) {
        this.key = key
        this.durationMs = durationMs
        startedAt = wallNowMs
        listenedMs = 0
        playingSince = if (key != null && playing) nowMs else NOT_PLAYING
    }

    /** Forget the song, for example when the session ends. */
    fun clear() {
        key = null
        listenedMs = 0
        playingSince = NOT_PLAYING
    }

    /** Tell the tracker that playback started or paused. */
    fun setPlaying(playing: Boolean, nowMs: Long) {
        if (key == null) {
            return
        }
        if (playing && playingSince == NOT_PLAYING) {
            playingSince = nowMs
        } else if (!playing && playingSince != NOT_PLAYING) {
            listenedMs += nowMs - playingSince
            playingSince = NOT_PLAYING
        }
    }

    /** How long the current song has been playing so far. */
    fun listened(nowMs: Long): Long =
        listenedMs + if (playingSince != NOT_PLAYING) nowMs - playingSince else 0L

    /**
     * Whether the song just started over, like when repeating a single song. That is the case when
     * the position jumps back to the start after most of the song was heard. Seeking back early on
     * does not count, as that is not a new play.
     *
     * @param positionMs The position the song is at now.
     */
    fun isLoop(positionMs: Long, nowMs: Long): Boolean =
        key != null &&
            durationMs > 0 &&
            positionMs <= LOOP_START_MS &&
            listened(nowMs) >= durationMs * LOOP_HEARD_SHARE

    /** The current play, or null if it has not been listened to long enough yet. */
    fun snapshot(nowMs: Long): Play? {
        if (key == null) {
            return null
        }
        val heard = listened(nowMs)
        return if (heard >= threshold(durationMs)) Play(startedAt, heard) else null
    }

    companion object {
        /** A song counts after this long, or after half of it for songs under a minute. */
        const val COUNT_AFTER_MS = 30_000L

        /** How close to the start the position must be for the song to have started over. */
        const val LOOP_START_MS = 3_000L

        /** How much of the song must have been heard before starting over counts as a loop. */
        const val LOOP_HEARD_SHARE = 0.9

        private const val NOT_PLAYING = -1L

        /** How long a song of [durationMs] must be listened to before it counts. */
        fun threshold(durationMs: Long) =
            if (durationMs > 0) min(COUNT_AFTER_MS, durationMs / 2) else COUNT_AFTER_MS
    }
}

/**
 * One play on its way to the database. The first write inserts it, every later write updates the
 * listened time of the same row, so a play that is still going on is already saved, and an app that
 * is killed halfway loses only the time since the last write.
 *
 * @param template Builds the play without its listened time. Called when it is first written.
 */
class PlayWrite(
    private val repository: StatsRepository,
    private val template: () -> PlayEvent,
) {
    private var id: Long? = null
    private val mutex = Mutex()

    suspend fun write(listenedMs: Long) = mutex.withLock {
        val saved = id
        if (saved == null) {
            id = repository.record(template().copy(listenedMs = listenedMs))
        } else {
            repository.updateListened(saved, listenedMs)
        }
    }
}
