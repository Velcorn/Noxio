/*
 * Copyright (c) 2026 Auxio Project
 * StatsPreferenceFragment.kt is part of Auxio.
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
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.R
import org.oxycblt.auxio.settings.BasePreferenceFragment
import org.oxycblt.auxio.stats.StatsBackup
import org.oxycblt.auxio.util.showToast

/**
 * The settings of the listening stats: the first day of the week and the automatic CSV backup.
 *
 * @author Noxio
 */
@AndroidEntryPoint
class StatsPreferenceFragment : BasePreferenceFragment(R.xml.preferences_stats) {
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
        findPreference<Preference>(getString(R.string.set_key_stats_backup_now))
            ?.setOnPreferenceClickListener {
                backUpNow()
                true
            }
        updateBackupSummaries()
    }

    /** Write the backup right away, replacing the file that is there. */
    private fun backUpNow() {
        val context = requireContext()
        if (statsBackup.folder == null) {
            folderLauncher.launch(null)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val saved =
                withContext(Dispatchers.IO) {
                    runCatching { statsBackup.run() }.getOrDefault(false)
                }
            context.showToast(
                if (saved) R.string.lng_stats_backup_done else R.string.err_stats_backup
            )
            updateBackupSummaries()
        }
    }

    private fun backupSwitch() =
        findPreference<SwitchPreferenceCompat>(getString(R.string.set_key_stats_backup))

    private fun updateBackupSummaries() {
        findPreference<Preference>(getString(R.string.set_key_stats_backup_folder))?.summary =
            statsBackup.folderName ?: getString(R.string.lbl_stats_backup_no_folder)
        val last = statsBackup.lastBackupMs
        findPreference<Preference>(getString(R.string.set_key_stats_backup_now))?.summary =
            if (last != null) {
                getString(
                    R.string.fmt_stats_backup_last,
                    DateUtils.getRelativeTimeSpanString(last).toString(),
                )
            } else {
                getString(R.string.set_stats_backup_now_desc)
            }
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
}
