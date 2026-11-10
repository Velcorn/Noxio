/*
 * Copyright (c) 2026 Auxio Project
 * PlayRecorderTest.kt is part of Auxio.
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

import java.io.OutputStream
import java.lang.reflect.Proxy
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.state.Progression
import org.oxycblt.musikr.Song
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

/**
 * The counting of plays as the player goes: a fake player, fake songs and a clock that only moves
 * when the test says so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayRecorderTest {
    private class FakeRepository : StatsRepository {
        val rows = mutableListOf<PlayEvent>()

        override suspend fun record(event: PlayEvent): Long {
            rows += event.copy(id = rows.size + 1L)
            return rows.last().id
        }

        override suspend fun updateListened(id: Long, listenedMs: Long) {
            val index = rows.indexOfFirst { it.id == id }
            rows[index] = rows[index].copy(listenedMs = listenedMs)
        }

        override suspend fun summary(range: LongRange, unknownArtist: String) = error("not used")

        override suspend fun exportCsv(out: OutputStream) = error("not used")

        override suspend fun importCsv(files: List<StatsCsvImport.Parsed>) = error("not used")
    }

    /** Only what the recorder asks the player for is answered, anything else fails the test. */
    private class FakePlayer {
        var song: Song? = null
        var progression: Progression = Progression.from(false, false, 0)
        val listeners = mutableListOf<PlaybackStateManager.Listener>()

        val manager: PlaybackStateManager =
            Proxy.newProxyInstance(
                PlaybackStateManager::class.java.classLoader,
                arrayOf(PlaybackStateManager::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getCurrentSong" -> song
                    "getProgression" -> progression
                    "addListener" -> listeners.add(args!![0] as PlaybackStateManager.Listener)
                    "removeListener" -> listeners.remove(args!![0])
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as PlaybackStateManager
    }

    private val durations = mutableMapOf<Song, Long>()
    private val keys = mutableMapOf<Song, String>()

    private fun song(key: String, durationMs: Long = 200_000): Song {
        val song =
            Proxy.newProxyInstance(Song::class.java.classLoader, arrayOf(Song::class.java)) {
                proxy,
                method,
                args ->
                when (method.name) {
                    "getDurationMs" -> durations.getValue(proxy as Song)
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    "toString" -> "song $key"
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as Song
        durations[song] = durationMs
        keys[song] = key
        return song
    }

    private val player = FakePlayer()
    private val repository = FakeRepository()
    private val main = TestScope()
    private val recorder =
        PlayRecorder(
            player.manager,
            repository,
            eventFor = { song, startedAt ->
                PlayEvent(
                    songUid = keys.getValue(song),
                    title = "Song ${keys.getValue(song)}",
                    artists = "Artist",
                    album = "Album",
                    startedAt = startedAt,
                    listenedMs = 0,
                    durationMs = durations.getValue(song),
                )
            },
            keyOf = { keys.getValue(it) },
            mainScope = main,
            writeScope = CoroutineScope(Dispatchers.Unconfined),
        )

    /** Let [ms] pass on both clocks: the one of the player and the one of the checkpoints. */
    private fun elapse(ms: Long) {
        var left = ms
        // In steps, so a checkpoint that is due sees the time it is due at.
        while (left > 0) {
            val step = minOf(left, 1_000L)
            ShadowSystemClock.advanceBy(Duration.ofMillis(step))
            main.testScheduler.advanceTimeBy(step)
            main.testScheduler.runCurrent()
            left -= step
        }
    }

    private fun playing(song: Song) {
        player.song = song
        player.progression = Progression.from(true, true, 0)
        recorder.attach()
    }

    private fun pause() {
        player.progression = Progression.from(false, false, 0)
        recorder.onProgressionChanged(player.progression)
    }

    private fun skipTo(song: Song) {
        player.song = song
        player.progression = Progression.from(true, true, 0)
        recorder.onIndexMoved(1)
        recorder.onProgressionChanged(player.progression)
    }

    @Test
    fun aSongHeardForLongEnoughIsSaved() {
        playing(song("a"))
        elapse(29_000)
        assertTrue(repository.rows.isEmpty())
        elapse(2_000)
        assertEquals(1, repository.rows.size)
        assertEquals("a", repository.rows.single().songUid)
    }

    @Test
    fun theSavedPlayIsKeptUpToDateInsteadOfAddedAgain() {
        playing(song("a"))
        elapse(31_000)
        elapse(30_000)
        assertEquals(1, repository.rows.size)
        assertTrue(repository.rows.single().listenedMs >= 60_000)
    }

    @Test
    fun pausingSavesTheTimeSoFarAndPausedTimeDoesNotCount() {
        playing(song("a"))
        elapse(40_000)
        pause()
        val saved = repository.rows.single().listenedMs
        assertTrue(saved in 40_000..41_000)
        elapse(120_000)
        assertEquals(saved, repository.rows.single().listenedMs)
    }

    @Test
    fun aSongThatIsPausedFromTheStartDoesNotCountUntilItPlays() {
        val song = song("a")
        player.song = song
        player.progression = Progression.from(false, false, 0)
        recorder.attach()
        elapse(120_000)
        assertTrue(repository.rows.isEmpty())
    }

    @Test
    fun aSongSkippedEarlyIsNotSavedAndTheNextOneStartsFresh() {
        playing(song("a"))
        elapse(10_000)
        skipTo(song("b"))
        assertTrue(repository.rows.isEmpty())
        elapse(31_000)
        assertEquals(listOf("b"), repository.rows.map { it.songUid })
    }

    @Test
    fun aShortSongCountsAfterHalfOfIt() {
        playing(song("a", durationMs = 20_000))
        elapse(16_000)
        assertEquals(1, repository.rows.size)
    }

    @Test
    fun repeatingOneSongIsANewPlayEachTime() {
        playing(song("a", durationMs = 100_000))
        elapse(95_000)
        assertEquals(1, repository.rows.size)
        // The song starts over.
        player.progression = Progression.from(true, true, 0)
        recorder.onProgressionChanged(player.progression)
        elapse(35_000)
        assertEquals(2, repository.rows.size)
        assertTrue(repository.rows.first().listenedMs >= 95_000)
    }

    @Test
    fun seekingBackEarlyIsNotANewPlay() {
        playing(song("a"))
        elapse(20_000)
        player.progression = Progression.from(true, true, 0)
        recorder.onProgressionChanged(player.progression)
        elapse(15_000)
        assertEquals(1, repository.rows.size)
    }

    @Test
    fun theEndOfTheSessionSavesTheLastPlayAndStopsTracking() {
        playing(song("a"))
        elapse(40_000)
        recorder.onSessionEnded()
        val saved = repository.rows.single().listenedMs
        elapse(120_000)
        assertEquals(1, repository.rows.size)
        assertEquals(saved, repository.rows.single().listenedMs)
    }

    @Test
    fun theRecorderListensWhileItIsAttachedOnly() {
        assertTrue(player.listeners.isEmpty())
        playing(song("a"))
        assertEquals(listOf<PlaybackStateManager.Listener>(recorder), player.listeners)
        recorder.release()
        assertTrue(player.listeners.isEmpty())
    }

    @Test
    fun aChangedQueueOrANewPlaybackFinishesThePlayOfTheSongBefore() {
        playing(song("a"))
        elapse(40_000)
        player.song = song("b")
        recorder.onQueueChanged(
            emptyList(),
            0,
            org.oxycblt.auxio.playback.state.QueueChange(
                org.oxycblt.auxio.playback.state.QueueChange.Type.MAPPING,
                org.oxycblt.auxio.list.adapter.UpdateInstructions.Diff,
            ),
        )
        assertTrue(repository.rows.single().listenedMs >= 40_000)
        player.song = song("c")
        recorder.onNewPlayback(null, emptyList(), 0, false)
        elapse(31_000)
        assertEquals(listOf("a", "c"), repository.rows.map { it.songUid })
    }

    @Test
    fun theFactoryMakesARecorderThatListensToThePlayer() {
        val manager = io.mockk.mockk<PlaybackStateManager>(relaxed = true)
        val made =
            PlayRecorder.Factory(
                    androidx.test.core.app.ApplicationProvider.getApplicationContext(),
                    manager,
                    FakeRepository(),
                )
                .create()
        made.attach()
        io.mockk.verify { manager.addListener(made) }
        made.release()
        io.mockk.verify { manager.removeListener(made) }
    }
}
