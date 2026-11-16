/*
 * Copyright (c) 2022 Auxio Project
 * AudioPreferenceFragment.kt is part of Auxio.
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
 
package org.oxycblt.auxio.settings.categories

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.settings.BasePreferenceFragment
import org.oxycblt.auxio.util.showToast
import timber.log.Timber as L

/** Audio settings, including the system equalizer and ReplayGain tags in music files. */
@AndroidEntryPoint
class AudioPreferenceFragment : BasePreferenceFragment(R.xml.preferences_audio) {
    private val playbackModel: PlaybackViewModel by activityViewModels()

    private val equalizerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        findPreference<Preference>(getString(R.string.set_key_equalizer))
            ?.setOnPreferenceClickListener {
                openEqualizer()
                true
            }
    }

    private fun openEqualizer() {
        L.d("Launching equalizer")
        val equalizerIntent =
            Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, playbackModel.currentAudioSessionId)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        try {
            equalizerLauncher.launch(equalizerIntent)
        } catch (e: ActivityNotFoundException) {
            requireContext().showToast(R.string.err_no_app)
        }
    }
}
