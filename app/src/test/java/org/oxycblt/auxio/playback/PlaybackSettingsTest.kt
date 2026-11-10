/*
 * Copyright (c) 2026 Auxio Project
 * PlaybackSettingsTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.IntegerTable
import org.oxycblt.auxio.R
import org.oxycblt.auxio.playback.replaygain.ReplayGainMode
import org.oxycblt.auxio.playback.replaygain.ReplayGainPreAmp
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val settings = PlaybackSettingsImpl(context)

    private fun key(res: Int) = context.getString(res)

    @Test
    fun theDefaults() {
        assertEquals(PlaySong.FromAll, settings.playInListWith)
        assertNull(settings.inParentPlaybackMode)
        assertEquals(ReplayGainMode.DYNAMIC, settings.replayGainMode)
        assertEquals(ReplayGainPreAmp(0f, 0f), settings.replayGainPreAmp)
        assertFalse(settings.headsetAutoplay)
        assertTrue(settings.keepShuffle)
        assertTrue(settings.rewindWithPrev)
        assertFalse(settings.pauseOnRepeat)
        assertFalse(settings.rememberPause)
        assertFalse(settings.exitOnTaskRemoval)
    }

    @Test
    fun theFlagsFollowTheirSettings() {
        preferences.edit {
            putBoolean(key(R.string.set_key_headset_autoplay), true)
            putBoolean(key(R.string.set_key_keep_shuffle), false)
            putBoolean(key(R.string.set_key_rewind_prev), false)
            putBoolean(key(R.string.set_key_repeat_pause), true)
            putBoolean(key(R.string.set_key_remember_pause), true)
            putBoolean(key(R.string.set_key_task_exit), true)
        }
        assertFalse(settings.keepShuffle)
        assertFalse(settings.rewindWithPrev)
        // Noxio has no settings for these any more, whatever is stored, they stay off.
        assertFalse(settings.headsetAutoplay)
        assertFalse(settings.pauseOnRepeat)
        assertFalse(settings.rememberPause)
        assertFalse(settings.exitOnTaskRemoval)
    }

    @Test
    fun theModesAreReadFromTheirCodes() {
        preferences.edit {
            putInt(key(R.string.set_key_play_in_list_with), PlaySong.FromAlbum.intCode)
            putInt(key(R.string.set_key_play_in_parent_with), PlaySong.FromGenre(null).intCode)
            putInt(key(R.string.set_key_replay_gain), IntegerTable.REPLAY_GAIN_MODE_ALBUM)
        }
        // Noxio has no settings for what plays any more: always the list a song was tapped in.
        assertEquals(PlaySong.FromAll, settings.playInListWith)
        assertNull(settings.inParentPlaybackMode)
        assertEquals(ReplayGainMode.ALBUM, settings.replayGainMode)
    }

    @Test
    fun thePreAmpIsKept() {
        settings.replayGainPreAmp = ReplayGainPreAmp(3.5f, -1.5f)
        assertEquals(ReplayGainPreAmp(3.5f, -1.5f), PlaybackSettingsImpl(context).replayGainPreAmp)
    }

    @Test
    fun theOldPlaybackModesBecomeTheNewOnes() {
        preferences.edit {
            putInt("auxio_library_playback_mode", IntegerTable.MUSIC_MODE_ALBUMS)
            putInt("auxio_detail_playback_mode", IntegerTable.MUSIC_MODE_GENRES)
        }
        settings.migrate()
        assertEquals(
            PlaySong.FromAlbum.intCode,
            preferences.getInt(key(R.string.set_key_play_in_list_with), 0),
        )
        assertEquals(
            PlaySong.FromGenre(null).intCode,
            preferences.getInt(key(R.string.set_key_play_in_parent_with), 0),
        )
        // The old settings are gone, so this does not happen again.
        assertFalse(preferences.contains("auxio_library_playback_mode"))
        assertFalse(preferences.contains("auxio_detail_playback_mode"))
    }

    @Test
    fun everyOldPlaybackModeHasANewOne() {
        val pairs =
            mapOf(
                IntegerTable.MUSIC_MODE_SONGS to PlaySong.FromAll,
                IntegerTable.MUSIC_MODE_ARTISTS to PlaySong.FromArtist(null),
                IntegerTable.MUSIC_MODE_GENRES to PlaySong.FromGenre(null),
                IntegerTable.MUSIC_MODE_ALBUMS to PlaySong.FromAlbum,
            )
        for ((old, new) in pairs) {
            preferences.edit { putInt("auxio_library_playback_mode", old) }
            settings.migrate()
            assertEquals(
                new.intCode,
                preferences.getInt(key(R.string.set_key_play_in_list_with), 0),
            )
        }
    }

    @Test
    fun migratingWithNothingOldChangesNothing() {
        settings.migrate()
        assertEquals(PlaySong.FromAll, settings.playInListWith)
    }

    @Test
    fun changesAreReportedToTheListener() {
        val reported = mutableListOf<String>()
        val listener =
            object : PlaybackSettings.Listener {
                override fun onReplayGainSettingsChanged() {
                    reported += "replay gain"
                }

                override fun onPauseOnRepeatChanged() {
                    reported += "pause on repeat"
                }
            }
        settings.registerListener(listener)
        preferences.edit {
            putInt(key(R.string.set_key_replay_gain), IntegerTable.REPLAY_GAIN_MODE_OFF)
        }
        preferences.edit { putFloat(key(R.string.set_key_pre_amp_with), 2f) }
        preferences.edit { putFloat(key(R.string.set_key_pre_amp_without), 2f) }
        preferences.edit { putBoolean(key(R.string.set_key_repeat_pause), true) }
        preferences.edit { putBoolean(key(R.string.set_key_keep_shuffle), false) }
        settings.unregisterListener(listener)
        preferences.edit { putBoolean(key(R.string.set_key_repeat_pause), false) }
        assertEquals(
            listOf("replay gain", "replay gain", "replay gain", "pause on repeat"),
            reported,
        )
    }
}
