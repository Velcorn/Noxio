/*
 * Copyright (c) 2026 Auxio Project
 * PlaybackStateManagerTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.state

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.oxycblt.auxio.list.adapter.UpdateInstructions
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Song

/**
 * The state of playback as the rest of the app sees it: it only mirrors what the player (the state
 * holder) reports and passes the commands on. The player is a mock, the callbacks are recorded.
 */
class PlaybackStateManagerTest {
    private var counter = 0

    private fun song(): Song {
        val id = Music.UID.fromString("uas%08d-0000-0000-0000-000000000000".format(counter++))!!
        return mockk(relaxed = true) { every { uid } returns id }
    }

    private val a = song()
    private val b = song()
    private val c = song()
    private val d = song()
    private val manager = PlaybackStateManagerImpl()

    /** A player whose queue and parent the test can change. */
    private class Holder(var queue: RawQueue, var parentOfQueue: MusicParent? = null) {
        val mock: PlaybackStateHolder =
            mockk(relaxed = true) {
                every { resolveQueue() } answers { queue }
                every { parent } answers { parentOfQueue }
                every { progression } returns Progression.from(true, true, 1_000)
                every { repeatMode } returns RepeatMode.ALL
                every { audioSessionId } returns 42
                every { handleDeferred(any()) } returns true
            }
    }

    private fun holder(vararg songs: Song, index: Int = 0, shuffle: List<Int> = emptyList()) =
        Holder(RawQueue(songs.toList(), shuffle, index))

    /** Records what the listeners are told. */
    private class Recorder : PlaybackStateManager.Listener {
        val events = mutableListOf<String>()
        val changes = mutableListOf<QueueChange>()
        var lastQueue: List<Song> = emptyList()
        var lastIndex = -2
        var lastShuffled: Boolean? = null

        override fun onIndexMoved(index: Int) {
            events += "index $index"
            lastIndex = index
        }

        override fun onQueueChanged(queue: List<Song>, index: Int, change: QueueChange) {
            events += "queue changed"
            changes += change
            lastQueue = queue
            lastIndex = index
        }

        override fun onQueueReordered(queue: List<Song>, index: Int, isShuffled: Boolean) {
            events += "queue reordered"
            lastQueue = queue
            lastIndex = index
            lastShuffled = isShuffled
        }

        override fun onNewPlayback(
            parent: MusicParent?,
            queue: List<Song>,
            index: Int,
            isShuffled: Boolean,
        ) {
            events += "new playback"
            lastQueue = queue
            lastIndex = index
            lastShuffled = isShuffled
        }

        override fun onProgressionChanged(progression: Progression) {
            events += "progression"
        }

        override fun onRepeatModeChanged(repeatMode: RepeatMode) {
            events += "repeat $repeatMode"
        }

        override fun onSessionEnded() {
            events += "session ended"
        }
    }

    private fun attach(holder: Holder): Holder {
        manager.registerStateHolder(holder.mock)
        return holder
    }

    // --- Before there is a player ---

    @Test
    fun theStateStartsEmpty() {
        assertNull(manager.currentSong)
        assertEquals(emptyList<Song>(), manager.queue)
        assertEquals(-1, manager.index)
        assertFalse(manager.isShuffled)
        assertEquals(RepeatMode.NONE, manager.repeatMode)
        assertNull(manager.parent)
        assertNull(manager.currentAudioSessionId)
        assertNull(manager.toSavedState())
    }

    @Test
    fun commandsWithoutAPlayerDoNothing() {
        manager.play(mockk(relaxed = true))
        manager.next()
        manager.prev()
        manager.goto(1)
        manager.playNext(listOf(a))
        manager.addToQueue(listOf(a))
        manager.moveQueueItem(0, 1)
        manager.removeQueueItem(0)
        manager.shuffled(true)
        manager.playing(true)
        manager.repeatMode(RepeatMode.ALL)
        manager.seekTo(5)
        manager.endSession()
        manager.requestAction(mockk(relaxed = true))
        assertNull(manager.currentSong)
    }

    // --- Registering the player ---

    @Test
    fun onlyOnePlayerIsRegisteredAtATime() {
        val first = attach(holder(a))
        val second = holder(b)
        manager.registerStateHolder(second.mock)
        manager.next()
        verify(exactly = 1) { first.mock.next() }
        verify(exactly = 0) { second.mock.next() }
        assertEquals(42, manager.currentAudioSessionId)
    }

    @Test
    fun aPlayerThatIsNotTheRegisteredOneCannotUnregisterIt() {
        val first = attach(holder(a))
        manager.unregisterStateHolder(holder(b).mock)
        manager.next()
        verify(exactly = 1) { first.mock.next() }
        manager.unregisterStateHolder(first.mock)
        manager.next()
        verify(exactly = 1) { first.mock.next() }
    }

    // --- Commands are passed on ---

    @Test
    fun theCommandsGoToThePlayer() {
        val player = attach(holder(a, b, c))
        val command: PlaybackCommand = mockk(relaxed = true)
        manager.play(command)
        manager.next()
        manager.prev()
        manager.goto(2)
        manager.shuffled(true)
        manager.playing(false)
        manager.repeatMode(RepeatMode.TRACK)
        manager.seekTo(1_234)
        manager.endSession()
        verify {
            player.mock.newPlayback(command)
            player.mock.next()
            player.mock.prev()
            player.mock.goto(2)
            player.mock.shuffled(true)
            player.mock.playing(false)
            player.mock.repeatMode(RepeatMode.TRACK)
            player.mock.seekTo(1_234)
            player.mock.endSession()
        }
    }

    @Test
    fun movingAndRemovingCarryWhatTheListsNeedToUpdate() {
        val player = attach(holder(a, b, c))
        manager.moveQueueItem(0, 2)
        manager.removeQueueItem(1)
        verify {
            player.mock.move(0, 2, StateAck.Move(0, 2))
            player.mock.remove(1, StateAck.Remove(1))
        }
    }

    @Test
    fun songsAddedWhileSomethingPlaysGoWhereTheListsExpectThem() {
        val player = attach(holder(a, b, c))
        manager.ack(player.mock, StateAck.NewPlayback)
        manager.playNext(listOf(d))
        manager.addToQueue(listOf(d, d))
        verify {
            player.mock.playNext(listOf(d), StateAck.PlayNext(1, 1))
            player.mock.addToQueue(listOf(d, d), StateAck.AddToQueue(3, 2))
        }
    }

    @Test
    fun songsAddedWhileNothingPlaysStartAFreshQueue() {
        val player = attach(holder())
        manager.playNext(listOf(a, b))
        manager.addToQueue(listOf(c))
        val commands = mutableListOf<PlaybackCommand>()
        verify(exactly = 2) { player.mock.newPlayback(capture(commands)) }
        assertEquals(listOf(a, b), commands[0].queue)
        assertEquals(listOf(c), commands[1].queue)
        assertFalse(commands[0].shuffled)
        assertNull(commands[0].song)
        assertNull(commands[0].parent)
    }

    // --- What the player reports ---

    @Test
    fun aNewPlaybackFillsTheStateAndTellsTheListeners() {
        val parent: MusicParent = mockk(relaxed = true)
        val player = attach(holder(a, b, c, index = 1))
        player.parentOfQueue = parent
        val recorder = Recorder()
        manager.addListener(recorder)
        manager.ack(player.mock, StateAck.NewPlayback)

        assertEquals(listOf(a, b, c), manager.queue)
        assertEquals(1, manager.index)
        assertEquals(b, manager.currentSong)
        assertEquals(parent, manager.parent)
        assertFalse(manager.isShuffled)
        assertEquals(listOf("new playback"), recorder.events)
        assertEquals(1, recorder.lastIndex)
    }

    @Test
    fun aShuffledQueueIsShownInItsShuffledOrder() {
        // The heap is a, b, c and the shuffle plays c, a, b.
        val player = attach(holder(a, b, c, index = 0, shuffle = listOf(2, 0, 1)))
        manager.ack(player.mock, StateAck.NewPlayback)
        assertEquals(listOf(c, a, b), manager.queue)
        assertEquals(1, manager.index)
        assertTrue(manager.isShuffled)
    }

    @Test
    fun theIndexMovingIsReported() {
        val player = attach(holder(a, b, c))
        manager.ack(player.mock, StateAck.NewPlayback)
        val recorder = Recorder()
        manager.addListener(recorder)
        recorder.events.clear()
        player.queue = RawQueue(listOf(a, b, c), emptyList(), 2)
        manager.ack(player.mock, StateAck.IndexMoved)
        assertEquals(c, manager.currentSong)
        assertEquals(listOf("index 2"), recorder.events)
    }

    @Test
    fun songsAddedAreReportedAsAMappingChange() {
        val player = attach(holder(a, b))
        manager.ack(player.mock, StateAck.NewPlayback)
        val recorder = Recorder()
        manager.addListener(recorder)
        player.queue = RawQueue(listOf(a, c, b), emptyList(), 0)
        manager.ack(player.mock, StateAck.PlayNext(1, 1))
        player.queue = RawQueue(listOf(a, c, b, d), emptyList(), 0)
        manager.ack(player.mock, StateAck.AddToQueue(3, 1))
        assertEquals(
            listOf(
                QueueChange(QueueChange.Type.MAPPING, UpdateInstructions.Add(1, 1)),
                QueueChange(QueueChange.Type.MAPPING, UpdateInstructions.Add(3, 1)),
            ),
            recorder.changes,
        )
        assertEquals(listOf(a, c, b, d), manager.queue)
    }

    @Test
    fun movingASongIsAnIndexChangeOnlyIfTheCurrentSongMoved() {
        val player = attach(holder(a, b, c, index = 0))
        manager.ack(player.mock, StateAck.NewPlayback)
        val recorder = Recorder()
        manager.addListener(recorder)
        // c moves to the front, a is now second: the index follows.
        player.queue = RawQueue(listOf(c, a, b), emptyList(), 1)
        manager.ack(player.mock, StateAck.Move(2, 0))
        // b and a swap places behind the current song: the index stays.
        player.queue = RawQueue(listOf(c, b, a), emptyList(), 1)
        manager.ack(player.mock, StateAck.Move(2, 1))
        assertEquals(QueueChange.Type.INDEX, recorder.changes[0].type)
        assertEquals(UpdateInstructions.Move(2, 0), recorder.changes[0].instructions)
        assertEquals(QueueChange.Type.MAPPING, recorder.changes[1].type)
    }

    @Test
    fun removingASongSaysWhatChanged() {
        val player = attach(holder(a, b, c, d, index = 1))
        manager.ack(player.mock, StateAck.NewPlayback)
        val recorder = Recorder()
        manager.addListener(recorder)
        // Removing the song behind the current one leaves everything else alone.
        player.queue = RawQueue(listOf(a, b, c), emptyList(), 1)
        manager.ack(player.mock, StateAck.Remove(3))
        // Removing one before the current one moves the index.
        player.queue = RawQueue(listOf(b, c), emptyList(), 0)
        manager.ack(player.mock, StateAck.Remove(0))
        // Removing the current one changes the song.
        player.queue = RawQueue(listOf(c), emptyList(), 0)
        manager.ack(player.mock, StateAck.Remove(0))
        assertEquals(
            listOf(QueueChange.Type.MAPPING, QueueChange.Type.INDEX, QueueChange.Type.SONG),
            recorder.changes.map { it.type },
        )
        assertEquals(UpdateInstructions.Remove(3, 1), recorder.changes[0].instructions)
    }

    @Test
    fun shufflingAndUnshufflingIsReported() {
        val player = attach(holder(a, b, c))
        manager.ack(player.mock, StateAck.NewPlayback)
        val recorder = Recorder()
        manager.addListener(recorder)
        recorder.events.clear()
        player.queue = RawQueue(listOf(a, b, c), listOf(1, 2, 0), 0)
        manager.ack(player.mock, StateAck.QueueReordered)
        assertEquals(listOf(b, c, a), manager.queue)
        assertTrue(manager.isShuffled)
        assertEquals(listOf("queue reordered"), recorder.events)
        assertEquals(true, recorder.lastShuffled)
    }

    @Test
    fun progressionRepeatModeAndTheEndOfTheSessionAreReported() {
        val player = attach(holder(a))
        val recorder = Recorder()
        manager.addListener(recorder)
        manager.ack(player.mock, StateAck.ProgressionChanged)
        manager.ack(player.mock, StateAck.RepeatModeChanged)
        manager.ack(player.mock, StateAck.SessionEnded)
        assertEquals(listOf("progression", "repeat ALL", "session ended"), recorder.events)
        assertTrue(manager.progression.isPlaying)
        assertEquals(RepeatMode.ALL, manager.repeatMode)
    }

    @Test
    fun anAckFromAnotherPlayerIsIgnored() {
        val player = attach(holder(a))
        val recorder = Recorder()
        manager.addListener(recorder)
        manager.ack(holder(b).mock, StateAck.NewPlayback)
        assertEquals(emptyList<String>(), recorder.events)
        assertNull(player.mock.takeIf { manager.currentSong != null })
    }

    // --- Listeners ---

    @Test
    fun aListenerAddedAfterPlaybackStartedGetsTheCurrentState() {
        val player = attach(holder(a, b, index = 1))
        manager.play(mockk(relaxed = true))
        manager.ack(player.mock, StateAck.NewPlayback)
        manager.ack(player.mock, StateAck.ProgressionChanged)
        val late = Recorder()
        manager.addListener(late)
        assertEquals(listOf("new playback", "progression", "repeat NONE"), late.events)
        assertEquals(listOf(a, b), late.lastQueue)
        assertEquals(1, late.lastIndex)
    }

    @Test
    fun aListenerThatIsRemovedHearsNothingMore() {
        val player = attach(holder(a))
        val recorder = Recorder()
        manager.addListener(recorder)
        manager.removeListener(recorder)
        manager.removeListener(recorder)
        manager.ack(player.mock, StateAck.SessionEnded)
        assertEquals(emptyList<String>(), recorder.events)
    }

    // --- Actions that wait for the player ---

    @Test
    fun anActionThePlayerCannotTakeYetWaitsForIt() {
        val action: DeferredPlayback = mockk(relaxed = true)
        manager.playDeferred(action)
        val player = attach(holder(a))
        verify { player.mock.handleDeferred(action) }
    }

    @Test
    fun anActionThePlayerTakesAtOnceDoesNotWait() {
        val first = attach(holder(a))
        val action: DeferredPlayback = mockk(relaxed = true)
        manager.playDeferred(action)
        verify(exactly = 1) { first.mock.handleDeferred(action) }
        manager.unregisterStateHolder(first.mock)
        val second = attach(holder(b))
        verify(exactly = 0) { second.mock.handleDeferred(action) }
    }

    @Test
    fun anActionThePlayerRefusedIsTakenOnTheNextRequest() {
        val player = holder(a)
        every { player.mock.handleDeferred(any()) } returnsMany listOf(false, true)
        manager.registerStateHolder(player.mock)
        val action: DeferredPlayback = mockk(relaxed = true)
        manager.playDeferred(action)
        manager.requestAction(player.mock)
        manager.requestAction(player.mock)
        verify(exactly = 2) { player.mock.handleDeferred(action) }
    }

    // --- Saving and restoring ---

    private fun savedState(
        heap: List<Song?>,
        index: Int,
        songUid: Music.UID,
        shuffled: List<Int> = emptyList(),
        positionMs: Long = 5_000,
    ) =
        PlaybackStateManager.SavedState(
            positionMs = positionMs,
            repeatMode = RepeatMode.ALL,
            parent = null,
            heap = heap,
            shuffledMapping = shuffled,
            index = index,
            songUid = songUid,
        )

    @Test
    fun thePlayingQueueCanBeSaved() {
        val player = attach(holder(a, b, c, index = 1, shuffle = listOf(2, 1, 0)))
        manager.ack(player.mock, StateAck.NewPlayback)
        manager.ack(player.mock, StateAck.RepeatModeChanged)
        val saved = manager.toSavedState()!!
        assertEquals(listOf<Song?>(a, b, c), saved.heap)
        assertEquals(listOf(2, 1, 0), saved.shuffledMapping)
        assertEquals(1, saved.index)
        assertEquals(b.uid, saved.songUid)
        assertEquals(RepeatMode.ALL, saved.repeatMode)
    }

    @Test
    fun aSavedQueueIsGivenToThePlayer() {
        val player = attach(holder())
        manager.applySavedState(savedState(listOf(a, b, c), index = 1, songUid = b.uid), false)
        val queue = mutableListOf<RawQueue>()
        verify {
            player.mock.applySavedState(
                null,
                capture(queue),
                5_000,
                RepeatMode.ALL,
                StateAck.NewPlayback,
            )
        }
        assertEquals(RawQueue(listOf(a, b, c), emptyList(), 1), queue.single())
    }

    @Test
    fun songsThatAreGoneAreLeftOutOfASavedQueueAndTheIndexFollows() {
        val player = attach(holder())
        // b is gone. The song that was playing, c, was at index 2 and is now at 1.
        manager.applySavedState(savedState(listOf(a, null, c), index = 2, songUid = c.uid), false)
        val queue = mutableListOf<RawQueue>()
        verify { player.mock.applySavedState(any(), capture(queue), any(), any(), any()) }
        assertEquals(RawQueue(listOf(a, c), emptyList(), 1), queue.single())
    }

    @Test
    fun aSavedShuffleKeepsItsOrderWithoutTheSongsThatAreGone() {
        val player = attach(holder())
        // The heap is a, gone, c, d and the shuffle plays d, a, gone, c.
        manager.applySavedState(
            savedState(
                listOf(a, null, c, d),
                index = 0,
                songUid = d.uid,
                shuffled = listOf(3, 0, 1, 2),
            ),
            false,
        )
        val queue = mutableListOf<RawQueue>()
        verify { player.mock.applySavedState(any(), capture(queue), any(), any(), any()) }
        assertEquals(RawQueue(listOf(a, c, d), listOf(2, 0, 1), 2), queue.single())
        assertEquals(listOf(d, a, c), queue.single().resolveSongs())
    }

    @Test
    fun ifTheSongThatWasPlayingIsGoneTheIndexMovesBackToOneThatIsThere() {
        val player = attach(holder())
        manager.applySavedState(savedState(listOf(a, b, c), index = 2, songUid = b.uid), false)
        val queue = mutableListOf<RawQueue>()
        verify { player.mock.applySavedState(any(), capture(queue), any(), any(), any()) }
        assertEquals(1, queue.single().heapIndex)
    }

    @Test
    fun aQueueWithNoSongLeftStartsOver() {
        val player = attach(holder())
        manager.applySavedState(savedState(listOf(null, null), index = 1, songUid = a.uid), false)
        verify { player.mock.reset(StateAck.NewPlayback) }
    }

    @Test
    fun aSavedQueueThatDoesNotAddUpIsAnError() {
        attach(holder())
        val broken = savedState(listOf(a, b), index = 0, songUid = a.uid, shuffled = listOf(0, 5))
        val failure = runCatching { manager.applySavedState(broken, false) }.exceptionOrNull()
        assertNotNull(failure)
    }

    @Test
    fun aSavedQueueIsNotAppliedOverOneThatIsPlayingUnlessAskedTo() {
        val player = attach(holder(a, b))
        manager.play(mockk(relaxed = true))
        val saved = savedState(listOf(c, d), index = 0, songUid = c.uid)
        manager.applySavedState(saved, destructive = false)
        verify(exactly = 0) { player.mock.applySavedState(any(), any(), any(), any(), any()) }
        manager.applySavedState(saved, destructive = true)
        verify(exactly = 1) { player.mock.applySavedState(any(), any(), any(), any(), any()) }
    }

    @Test
    fun aPlayerThatComesLaterGetsTheSavedState() {
        val first = attach(holder(a, b, index = 1))
        manager.play(mockk(relaxed = true))
        manager.ack(first.mock, StateAck.NewPlayback)
        manager.unregisterStateHolder(first.mock)
        val second = attach(holder())
        verify { second.mock.applySavedState(null, any(), any(), any(), null) }
    }
}
