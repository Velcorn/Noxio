/*
 * Copyright (c) 2021 Auxio Project
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
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.IndexingState
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.settings.ui.WrappedDialogPreference
import org.oxycblt.auxio.ui.blackenWindowOnShow
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.navigateSafe
import org.oxycblt.auxio.util.showToast
import org.oxycblt.musikr.IndexingProgress
import timber.log.Timber as L

/**
 * The [PreferenceFragmentCompat] that displays the root settings list.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class RootPreferenceFragment : BasePreferenceFragment(R.xml.preferences_root) {
    private val musicModel: MusicViewModel by activityViewModels()

    /** Set when the user started a scan here, so that the end of it is reported. */
    private var reportScan = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enterTransition = MaterialFadeThrough()
        returnTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectImmediately(musicModel.indexingState, ::updateIndexingState)
    }

    override fun onOpenDialogPreference(preference: WrappedDialogPreference) {
        when (preference.key) {
            getString(R.string.set_key_music_dirs) -> {
                findNavController()
                    .navigateSafe(RootPreferenceFragmentDirections.musicLocationsSettings())
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        // Hook generic preferences to their specified preferences
        // TODO: These seem like good things to put into a side navigation view, if I choose to
        //  do one.
        when (preference.key) {
            getString(R.string.set_key_ui) -> {
                L.d("Navigating to UI preferences")
                findNavController().navigateSafe(RootPreferenceFragmentDirections.uiPreferences())
            }
            getString(R.string.set_key_personalize) -> {
                L.d("Navigating to personalization preferences")
                findNavController()
                    .navigateSafe(RootPreferenceFragmentDirections.personalizePreferences())
            }
            getString(R.string.set_key_music) -> {
                L.d("Navigating to music preferences")
                findNavController()
                    .navigateSafe(RootPreferenceFragmentDirections.musicPreferences())
            }
            getString(R.string.set_key_audio) -> {
                L.d("Navigating to audio preferences")
                findNavController().navigateSafe(RootPreferenceFragmentDirections.audioPeferences())
            }
            getString(R.string.set_key_reindex) -> {
                reportScan = true
                requireContext().showToast(R.string.lbl_scan_started_refresh)
                musicModel.refresh()
            }
            getString(R.string.set_key_rescan) -> confirmRescan()
            else -> return super.onPreferenceTreeClick(preference)
        }

        return true
    }

    /** A rescan reads everything again and takes long, so it has to be confirmed. */
    private fun confirmRescan() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.set_rescan)
            .setMessage(R.string.lbl_rescan_confirm)
            .setPositiveButton(R.string.lbl_rescan) { _, _ ->
                reportScan = true
                requireContext().showToast(R.string.lbl_scan_started_rescan)
                musicModel.rescan()
            }
            .setNegativeButton(R.string.lbl_cancel, null)
            .create()
            .apply { blackenWindowOnShow() }
            .show()
    }

    /**
     * While the library is loading both options show how far it is and cannot be started again.
     * When a scan that was started here ends, say so.
     */
    private fun updateIndexingState(state: IndexingState?) {
        val reindex = findPreference<Preference>(getString(R.string.set_key_reindex)) ?: return
        val rescan = findPreference<Preference>(getString(R.string.set_key_rescan)) ?: return
        when (state) {
            is IndexingState.Indexing -> {
                val progress =
                    when (val p = state.progress) {
                        is IndexingProgress.Songs ->
                            getString(R.string.fmt_scan_progress, p.loaded, p.explored)
                        is IndexingProgress.Indeterminate -> getString(R.string.lbl_scan_working)
                    }
                reindex.summary = progress
                rescan.summary = progress
                reindex.isEnabled = false
                rescan.isEnabled = false
            }
            else -> {
                reindex.setSummary(R.string.set_reindex_desc)
                rescan.setSummary(R.string.set_rescan_desc)
                reindex.isEnabled = true
                rescan.isEnabled = true
                if (reportScan && state is IndexingState.Completed) {
                    reportScan = false
                    requireContext()
                        .showToast(
                            if (state.error == null) R.string.lbl_scan_done
                            else R.string.err_index_failed
                        )
                }
            }
        }
    }
}
