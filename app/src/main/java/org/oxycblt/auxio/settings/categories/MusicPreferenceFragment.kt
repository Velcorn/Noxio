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

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.settings.BasePreferenceFragment
import org.oxycblt.auxio.settings.ui.WrappedDialogPreference
import org.oxycblt.auxio.stats.StatsBackup
import org.oxycblt.auxio.util.navigateSafe
import timber.log.Timber as L

/**
 * "Content" settings.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class MusicPreferenceFragment : BasePreferenceFragment(R.xml.preferences_music) {
    private val musicModel: MusicViewModel by viewModels()
    @Inject lateinit var imageLoader: ImageLoader
    @Inject lateinit var statsBackup: StatsBackup

    private val folderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                // Keep write access to the folder across restarts, the backup runs in the
                // background.
                requireContext()
                    .contentResolver
                    .takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                statsBackup.folder = uri
                // Choosing a folder is a clear sign the user wants the backup.
                backupSwitch()?.isChecked = true
                statsBackup.enable()
                updateBackupSummaries()
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backupSwitch()?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                if (newValue == true) {
                    if (statsBackup.folder == null) {
                        // A folder is needed first, turning the switch on happens afterwards.
                        folderLauncher.launch(null)
                        false
                    } else {
                        statsBackup.enable()
                        true
                    }
                } else {
                    statsBackup.disable()
                    true
                }
            }
        findPreference<Preference>(getString(R.string.set_key_stats_backup_folder))
            ?.setOnPreferenceClickListener {
                folderLauncher.launch(null)
                true
            }
        updateBackupSummaries()
    }

    private fun backupSwitch() =
        findPreference<SwitchPreferenceCompat>(getString(R.string.set_key_stats_backup))

    private fun updateBackupSummaries() {
        findPreference<Preference>(getString(R.string.set_key_stats_backup_folder))?.summary =
            statsBackup.folderName ?: getString(R.string.lbl_stats_backup_no_folder)
        val last = statsBackup.lastBackupMs
        backupSwitch()?.summary =
            if (last != null) {
                getString(
                    R.string.fmt_stats_backup_last,
                    DateUtils.getRelativeTimeSpanString(last).toString(),
                )
            } else {
                getString(R.string.set_stats_backup_desc)
            }
    }

    override fun onOpenDialogPreference(preference: WrappedDialogPreference) {
        if (preference.key == getString(R.string.set_key_separators)) {
            L.d("Navigating to separator dialog")
            findNavController().navigateSafe(MusicPreferenceFragmentDirections.separatorsSettings())
        }
    }

    override fun onSetupPreference(preference: Preference) {
        if (preference.key == getString(R.string.set_key_cover_mode)) {
            L.d("Configuring cover mode setting")
            preference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                L.d("Cover mode changed, reloading music")
                musicModel.refresh()
                true
            }
        }
        if (preference.key == getString(R.string.set_key_square_covers)) {
            L.d("Configuring square cover setting")
            preference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                L.d("Cover mode changed, resetting image memory cache")
                imageLoader.memoryCache?.clear()
                true
            }
        }
        if (preference.key == getString(R.string.set_key_with_hidden)) {
            L.d("Configuring ignore hidden files setting")
            preference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                L.d("Ignore hidden files setting changed, reloading music")
                musicModel.refresh()
                true
            }
        }
    }
}
