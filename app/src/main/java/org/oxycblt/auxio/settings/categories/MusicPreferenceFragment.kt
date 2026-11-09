/*
 * Copyright (c) 2023 Auxio Project
 * MusicPreferenceFragment.kt is part of Auxio.
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

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.IndexingState
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.settings.BasePreferenceFragment
import org.oxycblt.auxio.settings.ui.WrappedDialogPreference
import org.oxycblt.auxio.ui.blackenWindowOnShow
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.navigateSafe
import org.oxycblt.auxio.util.showToast
import org.oxycblt.musikr.IndexingProgress
import timber.log.Timber as L

/**
 * The library settings: where the music is, loading it again, and how its tags are read.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class MusicPreferenceFragment : BasePreferenceFragment(R.xml.preferences_music) {
    private val musicModel: MusicViewModel by activityViewModels()

    /** Set when the user started a scan here, so that the end of it is reported. */
    private var reportScan = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectImmediately(musicModel.indexingState, ::updateIndexingState)
    }

    override fun onOpenDialogPreference(preference: WrappedDialogPreference) {
        when (preference.key) {
            getString(R.string.set_key_separators) -> {
                L.d("Navigating to separator dialog")
                findNavController()
                    .navigateSafe(MusicPreferenceFragmentDirections.separatorsSettings())
            }
            getString(R.string.set_key_music_dirs) -> {
                L.d("Navigating to music locations dialog")
                findNavController()
                    .navigateSafe(MusicPreferenceFragmentDirections.musicLocationsSettings())
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
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

    override fun onSetupPreference(preference: Preference) {
        if (preference.key == getString(R.string.set_key_with_hidden)) {
            L.d("Configuring ignore hidden files setting")
            preference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                L.d("Ignore hidden files setting changed, reloading music")
                musicModel.refresh()
                true
            }
        }
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
