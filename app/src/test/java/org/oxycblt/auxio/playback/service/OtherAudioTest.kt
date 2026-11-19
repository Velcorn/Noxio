/*
 * Copyright (c) 2026 Auxio Project
 * OtherAudioTest.kt is part of Auxio.
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.oxycblt.auxio.playback.service.OtherAudio.Action

class OtherAudioTest {
    private val media = 1
    private val voice = 2
    private val notification = 5
    private val music = 2
    private val speech = 1

    @Test
    fun aVoiceMessageWhileMusicPlaysPausesAndPlaysOnAfterwards() {
        val audio = OtherAudio()
        audio.onOwnPlaying(true, others = 0)
        assertEquals(Action.PAUSE, audio.onPlayersChanged(1, playing = true))
        assertTrue(audio.pausedForOther)
        assertEquals(Action.RESUME, audio.onPlayersChanged(0, playing = false))
        assertFalse(audio.pausedForOther)
    }

    @Test
    fun aPlayerThatWasThereAllAlongDoesNotPause() {
        val audio = OtherAudio()
        audio.onOwnPlaying(true, others = 1)
        assertEquals(Action.NONE, audio.onPlayersChanged(1, playing = true))
        // A second one does.
        assertEquals(Action.PAUSE, audio.onPlayersChanged(2, playing = true))
        assertEquals(Action.NONE, audio.onPlayersChanged(2, playing = false))
        assertEquals(Action.RESUME, audio.onPlayersChanged(1, playing = false))
    }

    @Test
    fun whileNoxioIsPausedByTheUserNothingHappens() {
        val audio = OtherAudio()
        audio.onOwnPlaying(false, others = 0)
        assertEquals(Action.NONE, audio.onPlayersChanged(1, playing = false))
        assertEquals(Action.NONE, audio.onPlayersChanged(0, playing = false))
    }

    @Test
    fun playingOrPausingByHandEndsTheWaitForTheOtherPlayer() {
        val audio = OtherAudio()
        audio.onOwnPlaying(true, others = 0)
        audio.onPlayersChanged(1, playing = true)
        // The user pauses or plays during the voice message: no playing on by itself later.
        audio.onOwnPlaying(false, others = 1)
        assertEquals(Action.NONE, audio.onPlayersChanged(0, playing = false))
    }

    @Test
    fun ownPlayerAndShortSoundsAreNotCounted() {
        val own = media to music
        assertEquals(0, OtherAudio.othersAmong(listOf(own), media, music))
        assertEquals(0, OtherAudio.othersAmong(listOf(own, notification to 4), media, music))
        assertEquals(1, OtherAudio.othersAmong(listOf(own, media to speech), media, music))
        assertEquals(1, OtherAudio.othersAmong(listOf(own, voice to speech), media, music))
        // Without its own player every relevant one is another app's.
        assertEquals(1, OtherAudio.othersAmong(listOf(media to speech), media, music))
        // A second music player is another app's.
        assertEquals(1, OtherAudio.othersAmong(listOf(own, own), media, music))
    }

    @Test
    fun onlyPlayersThatPlayCount() {
        assertTrue(OtherAudio.isStarted("AudioPlaybackConfiguration piid:3 state:started attr:x"))
        assertFalse(OtherAudio.isStarted("AudioPlaybackConfiguration piid:3 state:idle attr:x"))
        assertFalse(OtherAudio.isStarted("AudioPlaybackConfiguration piid:3 state:paused attr:x"))
        assertTrue(OtherAudio.isStarted("AudioPlaybackConfiguration piid:3 attr:x"))
    }
}
