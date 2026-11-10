/*
 * Copyright (c) 2023 Auxio Project
 * RootPreferenceFragment.kt is part of Auxio.
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
 
package org.oxycblt.auxio.settings

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.util.navigateSafe
import timber.log.Timber as L

/**
 * The [PreferenceFragmentCompat] that displays the root settings list: one row for each of the
 * settings screens.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class RootPreferenceFragment : BasePreferenceFragment(R.xml.preferences_root) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enterTransition = MaterialFadeThrough()
        returnTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        // Hook generic preferences to their specified preferences
        when (preference.key) {
            getString(R.string.set_key_ui) -> {
                L.d("Navigating to appearance preferences")
                findNavController().navigateSafe(RootPreferenceFragmentDirections.uiPreferences())
            }
            getString(R.string.set_key_personalize) -> {
                L.d("Navigating to home and player preferences")
                findNavController()
                    .navigateSafe(RootPreferenceFragmentDirections.personalizePreferences())
            }
            getString(R.string.set_key_audio) -> {
                L.d("Navigating to playback preferences")
                findNavController().navigateSafe(RootPreferenceFragmentDirections.audioPeferences())
            }
            getString(R.string.set_key_music) -> {
                L.d("Navigating to library preferences")
                findNavController()
                    .navigateSafe(RootPreferenceFragmentDirections.musicPreferences())
            }
            getString(R.string.set_key_backup_settings) -> {
                L.d("Navigating to backup preferences")
                findNavController()
                    .navigateSafe(RootPreferenceFragmentDirections.backupPreferences())
            }
            else -> return super.onPreferenceTreeClick(preference)
        }

        return true
    }
}
