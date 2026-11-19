/*
 * Copyright (c) 2026 Auxio Project
 * HomeSettingsFragment.kt is part of Auxio.
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.home.HomeViewModel
import org.oxycblt.auxio.settings.ui.BottomAlignedPreferenceLayoutManager
import org.oxycblt.auxio.util.systemBarInsetsCompat

/**
 * Noxio: the list of Settings as the last page of the home tabs, so it can be reached by swiping
 * past the music tabs. Its rows open the same screens as the Settings screen does.
 */
@AndroidEntryPoint
class HomeSettingsFragment : PreferenceFragmentCompat() {
    private val homeModel: HomeViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_root, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The tabs say where this is, so the title bar of the Settings screen is left out.
        view.findViewById<View>(R.id.preferences_bar)?.isVisible = false
    }

    override fun onCreateRecyclerView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?,
    ) =
        super.onCreateRecyclerView(inflater, parent, savedInstanceState).apply {
            clipToPadding = false
            layoutManager = BottomAlignedPreferenceLayoutManager(context)
            // The bottom inset of the home pages covers the playing strip and the tabs.
            setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.systemBarInsetsCompat
                updatePadding(top = bars.top, bottom = bars.bottom)
                insets
            }
        }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val key = preference.key ?: return super.onPreferenceTreeClick(preference)
        homeModel.showSettingsScreen(key)
        return true
    }
}
