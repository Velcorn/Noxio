/*
 * Copyright (c) 2023 Auxio Project
 * UIPreferenceFragment.kt is part of Auxio.
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

import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.settings.BasePreferenceFragment
import org.oxycblt.auxio.settings.ui.WrappedDialogPreference
import org.oxycblt.auxio.ui.LauncherIcon
import org.oxycblt.auxio.ui.UISettings
import org.oxycblt.auxio.util.isNight
import org.oxycblt.auxio.util.navigateSafe
import timber.log.Timber as L

/**
 * Display preferences.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class UIPreferenceFragment : BasePreferenceFragment(R.xml.preferences_ui) {
    @Inject lateinit var uiSettings: UISettings
    @Inject lateinit var imageLoader: ImageLoader
    private val musicModel: MusicViewModel by activityViewModels()

    override fun onOpenDialogPreference(preference: WrappedDialogPreference) {
        if (preference.key == getString(R.string.set_key_home_tabs)) {
            L.d("Navigating to home tab dialog")
            findNavController().navigateSafe(UIPreferenceFragmentDirections.tabSettings())
            return
        }
        if (preference.key == getString(R.string.set_key_accent)) {
            L.d("Navigating to accent dialog")
            findNavController().navigateSafe(UIPreferenceFragmentDirections.accentSettings())
        }
    }

    override fun onSetupPreference(preference: Preference) {
        when (preference.key) {
            getString(R.string.set_key_theme) -> {
                L.d("Configuring theme setting")
                preference.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { _, value ->
                        L.d("Theme changed, recreating")
                        requireActivity().recreate()
                        true
                    }
            }
            getString(R.string.set_key_accent) -> {
                L.d("Configuring accent setting")
                preference.summary = getString(uiSettings.accent.name)
            }
            getString(R.string.set_key_launcher_icon) -> {
                L.d("Configuring launcher icon setting")
                preference.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { _, value ->
                        LauncherIcon.fromIntCode(value as Int)?.let {
                            L.d("Switching launcher icon to $it")
                            LauncherIcon.apply(requireContext(), it)
                        }
                        true
                    }
            }
            getString(R.string.set_key_cover_mode) -> {
                L.d("Configuring cover mode setting")
                preference.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { _, _ ->
                        L.d("Cover mode changed, reloading music")
                        musicModel.refresh()
                        true
                    }
            }
            getString(R.string.set_key_square_covers) -> {
                L.d("Configuring square cover setting")
                preference.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { _, _ ->
                        L.d("Square covers changed, resetting image memory cache")
                        imageLoader.memoryCache?.clear()
                        true
                    }
            }
            getString(R.string.set_key_black_theme) -> {
                L.d("Configuring black theme setting")
                preference.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { _, _ ->
                        val activity = requireActivity()
                        if (activity.isNight) {
                            L.d("Black theme changed in night mode, recreating")
                            activity.recreate()
                        }

                        true
                    }
            }
        }
    }
}
