/*
 * Copyright (c) 2023 Auxio Project
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
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.settings.BasePreferenceFragment
import org.oxycblt.auxio.settings.ui.WrappedDialogPreference
import org.oxycblt.auxio.util.navigateSafe
import org.oxycblt.auxio.util.showToast
import timber.log.Timber as L

/**
 * Audio settings interface.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class AudioPreferenceFragment : BasePreferenceFragment(R.xml.preferences_audio) {
    private val playbackModel: PlaybackViewModel by activityViewModels()

    // AudioEffect expects you to use startActivityForResult with the panel intent. There is no
    // contract analogue for this intent, so the generic contract is used instead.
    private val equalizerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Nothing to do
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        findPreference<androidx.preference.Preference>(getString(R.string.set_key_equalizer))
            ?.setOnPreferenceClickListener {
                openEqualizer()
                true
            }
    }

    private fun openEqualizer() {
        // Launch the system equalizer app, if possible.
        L.d("Launching equalizer")
        val equalizerIntent =
            Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                // Provide audio session ID so the equalizer can show options for this app
                // in particular.
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, playbackModel.currentAudioSessionId)
                // Signal music type so that the equalizer settings are appropriate for
                // music playback.
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        try {
            equalizerLauncher.launch(equalizerIntent)
        } catch (e: ActivityNotFoundException) {
            requireContext().showToast(R.string.err_no_app)
        }
    }

    override fun onOpenDialogPreference(preference: WrappedDialogPreference) {
        if (preference.key == getString(R.string.set_key_pre_amp)) {
            L.d("Navigating to pre-amp dialog")
            findNavController().navigateSafe(AudioPreferenceFragmentDirections.preAmpSettings())
        }
    }
}
