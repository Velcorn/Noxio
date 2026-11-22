/*
 * Copyright (c) 2024 Auxio Project
 * ExoPlaybackStateHolder.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.oxycblt.auxio.R
import org.oxycblt.auxio.image.ImageSettings
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.playback.PlaybackSettings
import org.oxycblt.auxio.playback.persist.PersistenceRepository
import org.oxycblt.auxio.playback.replaygain.ReplayGainAudioProcessor
import org.oxycblt.auxio.playback.state.DeferredPlayback
import org.oxycblt.auxio.playback.state.PlaybackCommand
import org.oxycblt.auxio.playback.state.PlaybackStateHolder
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.state.Progression
import org.oxycblt.auxio.playback.state.QueueSwap
import org.oxycblt.auxio.playback.state.RawQueue
import org.oxycblt.auxio.playback.state.RepeatMode
import org.oxycblt.auxio.playback.state.ShuffleMode
import org.oxycblt.auxio.playback.state.StateAck
import org.oxycblt.auxio.playback.timer.SleepTimer
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Song
import timber.log.Timber as L

@OptIn(UnstableApi::class)
class ExoPlaybackStateHolder(
    private val context: Context,
    private val player: ExoPlayer,
    private val playbackManager: PlaybackStateManager,
    private val persistenceRepository: PersistenceRepository,
    private val playbackSettings: PlaybackSettings,
    private val commandFactory: PlaybackCommand.Factory,
    private val replayGainProcessor: ReplayGainAudioProcessor,
    private val musicRepository: MusicRepository,
    private val imageSettings: ImageSettings,
    sleepTimer: SleepTimer,
) :
    PlaybackStateHolder,
    Player.Listener,
    MusicRepository.UpdateListener,
    PlaybackSettings.Listener,
    ImageSettings.Listener {
    private val saveJob = Job()
    private val saveScope = CoroutineScope(Dispatchers.IO + saveJob)
    private val restoreScope = CoroutineScope(Dispatchers.IO + saveJob)
    private var currentSaveJob: Job? = null
    private val playbackFailures = PlaybackFailures()
    // While something plays the position is saved every little while, not only after changes.
    private val autoSaver =
        AutoSaver(CoroutineScope(Dispatchers.Main + saveJob), AUTO_SAVE_INTERVAL) {
            if (sessionOngoing) {
                val positionMs = playbackManager.progression.calculateElapsedPositionMs()
                saveScope.launch { persistenceRepository.savePosition(positionMs) }
            }
        }
    private var openAudioEffectSession = false
    private var sleepPauseAtEnd = false
    private val sleepTimerController =
        SleepTimerController(context, player, playbackManager, sleepTimer) {
            sleepPauseAtEnd = it
            updatePauseOnRepeat()
        }

    var sessionOngoing = false
        private set

    // Noxio: pausing for other apps' sounds that do not ask for audio focus, see OtherAudio.
    private val otherAudio = OtherAudio()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    /** Set while Noxio pauses or plays on because of another app, not because of the user. */
    private var otherAudioChange = false
    private val playbackCallback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(
                    configs: MutableList<AudioPlaybackConfiguration>,
                ) {
                    // Noxio's own player can briefly be there twice when the song changes, so
                    // only a change that lasts counts.
                    otherAudioHandler.removeCallbacks(checkOtherPlayers)
                    otherAudioHandler.postDelayed(checkOtherPlayers, OTHER_AUDIO_DELAY_MS)
                }
            }
        } else {
            null
        }

    private val otherAudioHandler = Handler(Looper.getMainLooper())
    private val checkOtherPlayers = Runnable { onOtherPlayers(currentOthers()) }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun othersPlaying(configs: List<AudioPlaybackConfiguration>) =
        OtherAudio.othersAmong(
            // Android does not tell other apps whether a player plays, but its description does;
            // players that only exist, like the idle ones of the phone app, are left out.
            configs
                .filter { OtherAudio.isStarted(it.toString()) }
                .map { it.audioAttributes.usage to it.audioAttributes.contentType },
            android.media.AudioAttributes.USAGE_MEDIA,
            android.media.AudioAttributes.CONTENT_TYPE_MUSIC,
        )

    private fun currentOthers() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null) {
            othersPlaying(audioManager.activePlaybackConfigurations)
        } else {
            0
        }

    private fun onOtherPlayers(others: Int) {
        if (!playbackSettings.pauseForOtherAudio) return
        when (otherAudio.onPlayersChanged(others, player.isPlaying)) {
            OtherAudio.Action.PAUSE -> {
                L.d("Another app plays a sound, pausing")
                otherAudioChange = true
                player.playWhenReady = false
            }
            OtherAudio.Action.RESUME -> {
                L.d("The other app's sound ended, playing on")
                otherAudioChange = true
                player.playWhenReady = true
            }
            OtherAudio.Action.NONE -> {}
        }
    }

    fun attach() {
        playbackManager.registerStateHolder(this)
        musicRepository.addUpdateListener(this)
        player.addListener(this)
        replayGainProcessor.attach()
        playbackSettings.registerListener(this)
        imageSettings.registerListener(this)
        sleepTimerController.attach()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playbackCallback != null) {
            audioManager?.registerAudioPlaybackCallback(playbackCallback, null)
        }
    }

    fun release() {
        saveJob.cancel()
        sleepTimerController.release()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playbackCallback != null) {
            audioManager?.unregisterAudioPlaybackCallback(playbackCallback)
        }
        otherAudioHandler.removeCallbacks(checkOtherPlayers)
        playbackManager.unregisterStateHolder(this)
        musicRepository.removeUpdateListener(this)
        player.removeListener(this)
        replayGainProcessor.release()
        imageSettings.unregisterListener(this)
        playbackSettings.unregisterListener(this)
        player.release()
    }

    override var parent: MusicParent? = null
        private set

    override val progression: Progression
        get() {
            val mediaItem = player.currentMediaItem ?: return Progression.nil()
            val duration = mediaItem.mediaMetadata.extras?.getLong("durationMs") ?: Long.MAX_VALUE
            val clampedPosition = player.currentPosition.coerceAtLeast(0).coerceAtMost(duration)
            return Progression.from(player.playWhenReady, player.isPlaying, clampedPosition)
        }

    override val repeatMode
        get() =
            when (val repeatMode = player.repeatMode) {
                Player.REPEAT_MODE_OFF -> RepeatMode.NONE
                Player.REPEAT_MODE_ONE -> RepeatMode.TRACK
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> throw IllegalStateException("Unknown repeat mode: $repeatMode")
            }

    override val audioSessionId: Int
        get() = player.audioSessionId

    override fun resolveQueue(): RawQueue {
        if (musicRepository.library == null && !earlyRestored) {
            // No library, cannot do anything. Noxio: unless the playing song was restored early.
            return RawQueue(emptyList(), emptyList(), 0)
        }
        val heap = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        val shuffledMapping =
            if (player.shuffleModeEnabled) {
                player.unscrambleQueueIndices()
            } else {
                emptyList()
            }
        return RawQueue(heap.mapNotNull { it.song }, shuffledMapping, player.currentMediaItemIndex)
    }

    /**
     * Noxio: whether only the playing song of the saved queue is there, restored before the library
     * had loaded. Until the rest follows, the saved queue must not be overwritten.
     */
    private var earlyRestored = false
    private var earlyRestoreStarted = false
    private var earlyPlayed = false

    /**
     * Noxio: restore the song that played with a stand-in from the tag cache, so it can play right
     * away instead of after the whole library has loaded (Auxio #1467). The rest of the queue is
     * added around it when the library is there, see [handleDeferred].
     */
    private fun restoreEarly(action: DeferredPlayback.RestoreState) {
        if (earlyRestoreStarted) return
        earlyRestoreStarted = true
        restoreScope.launch {
            val state = persistenceRepository.readEarlyState() ?: return@launch
            withContext(Dispatchers.Main) {
                // The library may have won the race, then the usual restore does it.
                if (musicRepository.library?.takeIf { !it.empty() } != null) return@withContext
                L.d("Restoring the playing song before the library")
                earlyRestored = true
                playbackManager.applySavedState(state, false)
                if (action.play) {
                    earlyPlayed = true
                    playbackManager.playing(true)
                }
            }
        }
    }

    /** Noxio: add the rest of the saved queue around the song restored early. */
    private fun completeEarlyRestore(action: DeferredPlayback.RestoreState) {
        restoreScope.launch {
            val state = persistenceRepository.readState()
            withContext(Dispatchers.Main) {
                earlyRestored = false
                val current = playbackManager.currentSong
                if (state != null && current != null && state.songUid == current.uid) {
                    L.d("Adding the rest of the queue around the playing song")
                    // Where it plays now, not where it was saved.
                    playbackManager.applySavedState(
                        state.copy(
                            positionMs = playbackManager.progression.calculateElapsedPositionMs(),
                        ),
                        true,
                    )
                }
                // A request to play that came after the early restore still plays.
                if (action.play && !earlyPlayed) playbackManager.playing(true)
            }
        }
    }

    override fun handleDeferred(action: DeferredPlayback): Boolean {
        val library = musicRepository.library?.takeIf { !it.empty() }
        if (library == null) {
            // Noxio: no library yet, but the saved queue can already be built from the tag
            // cache. The action stays pending, the library still has to arrive.
            if (action is DeferredPlayback.RestoreState) restoreEarly(action)
            return false
        }

        when (action) {
            // Restore state -> Start a new restoreState job
            is DeferredPlayback.RestoreState -> {
                if (earlyRestored) {
                    // Noxio: the playing song is already there, the rest of the queue follows.
                    completeEarlyRestore(action)
                    return true
                }
                L.d("Restoring playback state")
                restoreScope.launch {
                    val state = persistenceRepository.readState()
                    withContext(Dispatchers.Main) {
                        if (state != null) {
                            // Apply the saved state on the main thread to prevent code expecting
                            // state updates on the main thread from crashing.
                            playbackManager.applySavedState(state, false)
                            if (action.play) {
                                playbackManager.playing(true)
                            }
                        } else if (action.fallback != null) {
                            playbackManager.playDeferred(action.fallback)
                        }
                    }
                }
            }
            // Shuffle all -> Start new playback from all songs
            is DeferredPlayback.ShuffleAll -> {
                L.d("Shuffling all tracks")
                playbackManager.play(
                    requireNotNull(commandFactory.all(ShuffleMode.ON)) {
                        "Invalid playback parameters"
                    },
                )
            }
            // Open -> Try to find the Song for the given file and then play it from all songs
            is DeferredPlayback.Open -> {
                L.d("Opening specified file")
                context.applicationContext.contentResolver
                    .query(
                        action.uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                        null,
                        null,
                        null,
                    )
                    ?.use { cursor ->
                        val displayNameIndex =
                            cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            val displayName = cursor.getString(displayNameIndex)
                            val size = cursor.getLong(sizeIndex)
                            val song =
                                library.songs.find {
                                    it.path.name == displayName && it.size == size
                                }
                            if (song != null) {
                                val command =
                                    requireNotNull(
                                        commandFactory.songFromAll(song, ShuffleMode.IMPLICIT),
                                    ) {
                                        "Invalid playback command"
                                    }
                                playbackManager.play(command)
                            }
                        }
                    }
            }
        }

        return true
    }

    override fun playing(playing: Boolean) {
        player.playWhenReady = playing
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        deferSave()
        // Ack handled w/ExoPlayer events
    }

    override fun repeatMode(repeatMode: RepeatMode) {
        player.repeatMode =
            when (repeatMode) {
                RepeatMode.NONE -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.TRACK -> Player.REPEAT_MODE_ONE
            }
        updatePauseOnRepeat()
        playbackManager.ack(this, StateAck.RepeatModeChanged)
        deferSave()
    }

    override fun newPlayback(command: PlaybackCommand) {
        // Noxio: something new plays, the queue restored early is replaced and may be saved over.
        earlyRestored = false
        playbackFailures.reset()
        parent = command.parent
        player.shuffleModeEnabled = command.shuffled
        player.setMediaItems(command.queue.map { it.buildMediaItem() })
        val startIndex =
            command.song
                ?.let { command.queue.indexOf(it) }
                .also { check(it != -1) { "Start song not in queue" } }
        if (command.shuffled) {
            player.setShuffleOrder(BetterShuffleOrder(command.queue.size, startIndex ?: -1))
        }
        val target = startIndex ?: player.currentTimeline.getFirstWindowIndex(command.shuffled)
        player.seekTo(target, C.TIME_UNSET)
        player.prepare()
        player.play()
        playbackManager.ack(this, StateAck.NewPlayback)
        deferSave()
    }

    override fun shuffled(shuffled: Boolean) {
        player.setShuffleModeEnabled(shuffled)
        if (player.shuffleModeEnabled) {
            // Have to manually refresh the shuffle seed and anchor it to the new current songs
            player.setShuffleOrder(
                BetterShuffleOrder(player.mediaItemCount, player.currentMediaItemIndex),
            )
        }
        playbackManager.ack(this, StateAck.QueueReordered)
        deferSave()
    }

    override fun reorder(order: List<Int>) {
        // The positions of the order are the shown ones, ExoPlayer moves by the real ones.
        val real = player.unscrambleQueueIndices()
        if (real.size != order.size) {
            return
        }
        // Moving one item after the other into place keeps the current song playing, where
        // setting the items anew would restart it. current[position] is the real index of the
        // item that is at that position right now.
        val current = MutableList(real.size) { it }
        for (target in order.indices) {
            val wanted = real[order[target]]
            val from = current.indexOf(wanted)
            if (from != target) {
                player.moveMediaItem(from, target)
                current.add(target, current.removeAt(from))
            }
        }
        // The order of the queue is the order now, so there is nothing left to shuffle.
        player.shuffleModeEnabled = false
        playbackManager.ack(this, StateAck.QueueReordered)
        deferSave()
    }

    override fun next() {
        // Replicate the old pseudo-circular queue behavior when no repeat option is implemented.
        // Basically, you can't skip back and wrap around the queue, but you can skip forward and
        // wrap around the queue, albeit playback will be paused.
        if (player.repeatMode == Player.REPEAT_MODE_ALL || player.hasNextMediaItem()) {
            player.seekToNext()
            if (!playbackSettings.rememberPause) {
                player.play()
            }
        } else {
            player.seekTo(
                player.currentTimeline.getFirstWindowIndex(player.shuffleModeEnabled),
                C.TIME_UNSET,
            )
            // TODO: Dislike the UX implications of this, I feel should I bite the bullet
            //  and switch to dynamic skip enable/disable?
            if (!playbackSettings.rememberPause) {
                player.pause()
            }
        }
        playbackManager.ack(this, StateAck.IndexMoved)
        deferSave()
    }

    override fun prev() {
        if (playbackSettings.rewindWithPrev) {
            player.seekToPrevious()
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0)
        }
        if (!playbackSettings.rememberPause) {
            player.play()
        }
        playbackManager.ack(this, StateAck.IndexMoved)
        deferSave()
    }

    override fun goto(index: Int) {
        val indices = player.unscrambleQueueIndices()
        if (indices.isEmpty()) {
            return
        }

        val trueIndex = indices[index]
        player.seekTo(trueIndex, C.TIME_UNSET) // Handles remaining custom logic
        if (!playbackSettings.rememberPause) {
            player.play()
        }
        playbackManager.ack(this, StateAck.IndexMoved)
        deferSave()
    }

    override fun playNext(songs: List<Song>, ack: StateAck.PlayNext) {
        if (player.mediaItemCount == 0) {
            player.addMediaItems(songs.map { it.buildMediaItem() })
        } else {
            val currentIndex = player.currentMediaItemIndex
            val order =
                if (player.shuffleModeEnabled) {
                    BetterShuffleOrder(player.unscrambleQueueIndices().toIntArray())
                        .insertAfter(currentIndex, songs.size)
                } else {
                    null
                }
            player.addMediaItems(currentIndex + 1, songs.map { it.buildMediaItem() })
            if (order != null) player.setShuffleOrder(order)
        }
        playbackManager.ack(this, ack)
        deferSave()
    }

    override fun addToQueue(songs: List<Song>, ack: StateAck.AddToQueue) {
        player.addMediaItems(songs.map { it.buildMediaItem() })
        playbackManager.ack(this, ack)
        deferSave()
    }

    override fun move(from: Int, to: Int, ack: StateAck.Move) {
        val indices = player.unscrambleQueueIndices()
        if (indices.isEmpty()) {
            return
        }

        val trueFrom = indices[from]
        val trueTo = indices[to]
        // ExoPlayer does not actually update it's ShuffleOrder when moving items. Retain a
        // semblance of "normalcy" by doing a weird no-op swap that actually moves the item.
        when {
            trueFrom > trueTo -> {
                player.moveMediaItem(trueFrom, trueTo)
                player.moveMediaItem(trueTo + 1, trueFrom)
            }
            trueTo > trueFrom -> {
                player.moveMediaItem(trueFrom, trueTo)
                player.moveMediaItem(trueTo - 1, trueFrom)
            }
        }
        playbackManager.ack(this, ack)
        deferSave()
    }

    override fun remove(at: Int, ack: StateAck.Remove) {
        val indices = player.unscrambleQueueIndices()
        if (indices.isEmpty()) {
            return
        }

        val trueIndex = indices[at]
        val songWillChange = player.currentMediaItemIndex == trueIndex
        player.removeMediaItem(trueIndex)
        if (songWillChange && !playbackSettings.rememberPause) {
            player.play()
        }
        playbackManager.ack(this, ack)
        deferSave()
    }

    override fun applySavedState(
        parent: MusicParent?,
        rawQueue: RawQueue,
        positionMs: Long,
        repeatMode: RepeatMode,
        ack: StateAck.NewPlayback?,
    ) {
        var sendNewPlaybackEvent = false
        var shouldSeek = false
        if (this.parent != parent) {
            this.parent = parent
            sendNewPlaybackEvent = true
        }
        val current = resolveQueue()
        if (rawQueue != current && QueueSwap.isSameQueue(current, rawQueue)) {
            // Noxio: the same songs as new objects (the library was loaded again, or the songs
            // of an early restore are replaced by the real ones). Replacing the items keeps the
            // player where it is, without a gap.
            L.d("Replacing the songs of the queue in place")
            player.replaceMediaItems(
                0,
                player.mediaItemCount,
                rawQueue.heap.map { it.buildMediaItem() },
            )
            sendNewPlaybackEvent = true
        } else if (QueueSwap.isGrowth(current, rawQueue)) {
            // Noxio: the song restored early gets the rest of its queue around it, while it
            // keeps playing.
            L.d("Growing the queue around the playing song")
            val heap = rawQueue.heap.map { it.buildMediaItem() }
            val index = rawQueue.heapIndex
            player.replaceMediaItem(0, heap[index])
            player.addMediaItems(1, heap.subList(index + 1, heap.size))
            player.addMediaItems(0, heap.subList(0, index))
            if (rawQueue.isShuffled) {
                player.shuffleModeEnabled = true
                player.setShuffleOrder(BetterShuffleOrder(rawQueue.shuffledMapping.toIntArray()))
            } else {
                player.shuffleModeEnabled = false
            }
            sendNewPlaybackEvent = true
        } else if (rawQueue != current) {
            player.setMediaItems(rawQueue.heap.map { it.buildMediaItem() })
            if (rawQueue.isShuffled) {
                player.shuffleModeEnabled = true
                player.setShuffleOrder(BetterShuffleOrder(rawQueue.shuffledMapping.toIntArray()))
            } else {
                player.shuffleModeEnabled = false
            }
            player.seekTo(rawQueue.heapIndex, C.TIME_UNSET)
            player.prepare()
            player.pause()
            sendNewPlaybackEvent = true
            shouldSeek = true
        }

        repeatMode(repeatMode)
        // See if we differ by more than a second. This allows us to avoid a meaningless seek
        // in the case of a "tight restore" (i.e music was reloaded).
        // In the case that this is a false positive, it's not very percievable (at least compared
        // to skipping when updating the library).
        // TODO: Introduce a better state management system rather than do something finicky like
        // this.
        if (shouldSeek || abs(player.currentPosition - positionMs) > 1000L) {
            player.seekTo(positionMs)
        }

        if (sendNewPlaybackEvent) {
            ack?.let { playbackManager.ack(this, it) }
        }
    }

    override fun endSession() {
        // This session has ended, so we need to reset this flag for when the next
        // session starts.
        playbackManager.playing(false)
        save {
            // User could feasibly start playing again if they were fast enough, so
            // we need to avoid stopping the foreground state if that's the case.
            if (!playbackManager.progression.isPlaying) {
                sessionOngoing = false
                playbackManager.ack(this, StateAck.SessionEnded)
            }
        }
    }

    override fun reset(ack: StateAck.NewPlayback) {
        player.setMediaItems(listOf())
        playbackManager.ack(this, ack)
        deferSave()
    }

    // --- PLAYER OVERRIDES ---

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        super.onPlayWhenReadyChanged(playWhenReady, reason)

        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
            sleepTimerController.onTrackEnded()
        }

        if (player.playWhenReady) {
            // Mark that we have started playing so that the notification can now be posted.
            L.d("Player has started playing")
            sessionOngoing = true
            if (!openAudioEffectSession) {
                // Convention to start an audioeffect session on play/pause rather than
                // start/stop
                L.d("Opening audio effect session")
                broadcastAudioEffectAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                openAudioEffectSession = true
            }
        } else if (openAudioEffectSession) {
            // Make sure to close the audio session when we stop playback.
            L.d("Closing audio effect session")
            broadcastAudioEffectAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            openAudioEffectSession = false
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)

        if (playbackState == Player.STATE_ENDED) playbackFailures.reset()
        if (playbackState == Player.STATE_ENDED && player.repeatMode == Player.REPEAT_MODE_OFF) {
            goto(0)
            player.pause()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)

        // Only a completed track breaks a chain of failures; starting to play is insufficient.
        if (
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        ) {
            playbackFailures.reset()
        }
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            playbackManager.ack(this, StateAck.IndexMoved)
            deferSave()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        autoSaver.setPlaying(isPlaying)
        // Only a change by the user (or anything else) starts over what counts as another sound.
        if (otherAudioChange) {
            otherAudioChange = false
        } else {
            otherAudio.onOwnPlaying(isPlaying, currentOthers())
        }
        // Where it stopped or started is worth keeping, however it was started or stopped.
        deferSave()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        super.onEvents(player, events)

        // So many actions trigger progression changes that it becomes easier just to handle it
        // in an ExoPlayer callback anyway. This doesn't really cause issues anywhere.
        if (
            events.containsAny(
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
            )
        ) {
            L.d("Player state changed, must synchronize state")
            playbackManager.ack(this, StateAck.ProgressionChanged)
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        L.e("Player error occurred")
        L.e(error.stackTraceToString())
        val title =
            player.currentMediaItem?.song?.name?.resolve(context)
                ?: player.currentMediaItem?.mediaMetadata?.title
                ?: context.getString(R.string.lbl_song)
        Toast.makeText(
                context,
                context.getString(R.string.err_song_playback, title),
                Toast.LENGTH_SHORT,
            )
            .show()
        if (playbackFailures.shouldStop(player.mediaItemCount)) {
            player.stop()
            player.playWhenReady = false
            return
        }
        // A failed track must not be retried by repeat-one. Wrap once to try the entire queue.
        val nextIndex =
            player.currentTimeline.getNextWindowIndex(
                player.currentMediaItemIndex,
                Player.REPEAT_MODE_ALL,
                player.shuffleModeEnabled,
            )
        if (nextIndex == C.INDEX_UNSET) {
            player.stop()
            player.playWhenReady = false
            return
        }
        player.seekTo(nextIndex, C.TIME_UNSET)
        player.prepare()
        playbackManager.ack(this, StateAck.IndexMoved)
        deferSave()
    }

    private fun broadcastAudioEffectAction(event: String) {
        L.d("Broadcasting AudioEffect event: $event")
        context.sendBroadcast(
            Intent(event)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC),
        )
    }

    // --- MUSICREPOSITORY METHODS ---

    override fun onMusicChanges(changes: MusicRepository.Changes) {
        if (changes.deviceLibrary && musicRepository.library?.takeIf { !it.empty() } != null) {
            // We now have a library, see if we have anything we need to do.
            L.d("Library obtained, requesting action")
            playbackManager.requestAction(this)
        }
    }

    // --- PLAYBACKSETTINGS OVERRIDES ---

    override fun onPauseOnRepeatChanged() {
        super.onPauseOnRepeatChanged()
        updatePauseOnRepeat()
    }

    private fun updatePauseOnRepeat() {
        player.pauseAtEndOfMediaItems =
            sleepPauseAtEnd ||
                (player.repeatMode == Player.REPEAT_MODE_ONE && playbackSettings.pauseOnRepeat)
    }

    private fun save(cb: () -> Unit) {
        // Noxio: not while only the song restored early is there, that would lose the queue.
        val partial = earlyRestored
        saveJob {
            if (sessionOngoing && !partial) {
                persistenceRepository.saveState(playbackManager.toSavedState())
            }
            withContext(Dispatchers.Main) { cb() }
        }
    }

    private fun deferSave() {
        saveJob {
            L.d("Waiting for save buffer")
            delay(SAVE_BUFFER.milliseconds)
            yield()
            L.d("Committing saved state")
            if (sessionOngoing && !earlyRestored) {
                persistenceRepository.saveState(playbackManager.toSavedState())
            }
        }
    }

    private fun saveJob(block: suspend () -> Unit) {
        currentSaveJob?.let {
            L.d("Discarding prior save job")
            it.cancel()
        }
        currentSaveJob = saveScope.launch { block() }
    }

    private fun Song.buildMediaItem() = MediaItem.Builder().setUri(uri).setTag(this).build()

    private val MediaItem.song: Song?
        get() = this.localConfiguration?.tag as? Song?

    private fun Player.unscrambleQueueIndices(): List<Int> {
        val timeline = currentTimeline
        if (timeline.isEmpty) {
            return emptyList()
        }
        val queue = mutableListOf<Int>()

        // Add the active queue item.
        val currentMediaItemIndex = currentMediaItemIndex
        queue.add(currentMediaItemIndex)

        // Fill queue alternating with next and/or previous queue items.
        var firstMediaItemIndex = currentMediaItemIndex
        var lastMediaItemIndex = currentMediaItemIndex
        val shuffleModeEnabled = shuffleModeEnabled
        while ((firstMediaItemIndex != C.INDEX_UNSET || lastMediaItemIndex != C.INDEX_UNSET)) {
            // Begin with next to have a longer tail than head if an even sized queue needs to be
            // trimmed.
            if (lastMediaItemIndex != C.INDEX_UNSET) {
                lastMediaItemIndex =
                    timeline.getNextWindowIndex(
                        lastMediaItemIndex,
                        Player.REPEAT_MODE_OFF,
                        shuffleModeEnabled,
                    )
                if (lastMediaItemIndex != C.INDEX_UNSET) {
                    queue.add(lastMediaItemIndex)
                }
            }
            if (firstMediaItemIndex != C.INDEX_UNSET) {
                firstMediaItemIndex =
                    timeline.getPreviousWindowIndex(
                        firstMediaItemIndex,
                        Player.REPEAT_MODE_OFF,
                        shuffleModeEnabled,
                    )
                if (firstMediaItemIndex != C.INDEX_UNSET) {
                    queue.add(0, firstMediaItemIndex)
                }
            }
        }

        return queue
    }

    class Factory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val playbackManager: PlaybackStateManager,
        private val persistenceRepository: PersistenceRepository,
        private val playbackSettings: PlaybackSettings,
        private val commandFactory: PlaybackCommand.Factory,
        private val mediaSourceFactory: MediaSource.Factory,
        private val replayGainProcessor: ReplayGainAudioProcessor,
        private val musicRepository: MusicRepository,
        private val imageSettings: ImageSettings,
        private val sleepTimer: SleepTimer,
    ) {
        fun create(): ExoPlaybackStateHolder {
            // Since Auxio is a music player, only specify an audio renderer to save
            // battery/apk size/cache size]
            val audioRenderer = RenderersFactory { handler, _, audioListener, _, _ ->
                arrayOf<BaseRenderer>(
                    FfmpegAudioRenderer(handler, audioListener, replayGainProcessor),
                    MediaCodecAudioRenderer(
                        context,
                        MediaCodecSelector.DEFAULT,
                        handler,
                        audioListener,
                        DefaultAudioSink.Builder(context)
                            .setAudioProcessors(arrayOf(replayGainProcessor))
                            .build(),
                    ),
                )
            }

            val exoPlayer =
                ExoPlayer.Builder(context, audioRenderer)
                    .setMediaSourceFactory(mediaSourceFactory)
                    // Enable automatic WakeLock support
                    .setWakeMode(C.WAKE_MODE_LOCAL)
                    .setAudioAttributes(
                        // Signal that we are a music player.
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .build(),
                        true,
                    )
                    .build()

            return ExoPlaybackStateHolder(
                context,
                exoPlayer,
                playbackManager,
                persistenceRepository,
                playbackSettings,
                commandFactory,
                replayGainProcessor,
                musicRepository,
                imageSettings,
                sleepTimer,
            )
        }
    }

    private companion object {
        /** How long another app's player has to be there before it counts. */
        private const val OTHER_AUDIO_DELAY_MS = 700L

        const val SAVE_BUFFER = 5000L
        const val AUTO_SAVE_INTERVAL = 15_000L
    }
}
