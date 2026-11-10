/*
 * Copyright (c) 2026 Auxio Project
 * BackupPreferenceFragment.kt is part of Auxio.
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
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.R
import org.oxycblt.auxio.backup.AppBackup
import org.oxycblt.auxio.backup.BackupArchive
import org.oxycblt.auxio.backup.BackupData
import org.oxycblt.auxio.backup.BackupSchedule
import org.oxycblt.auxio.backup.BackupSchedule.Frequency
import org.oxycblt.auxio.settings.BasePreferenceFragment
import org.oxycblt.auxio.settings.ui.IntListPreference
import org.oxycblt.auxio.ui.blackenWindowOnShow
import org.oxycblt.auxio.util.showToast

/**
 * The backup settings: when the backup of the settings, listening history and playlists runs on its
 * own, its folder, a backup right away and restoring one.
 *
 * @author Noxio
 */
@AndroidEntryPoint
class BackupPreferenceFragment : BasePreferenceFragment(R.xml.preferences_backup) {
    @Inject lateinit var statsBackup: AppBackup

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
                // Choosing a folder is a clear sign the user wants the backup: one that is off
                // is turned on, once a week (which also makes a backup right away).
                val frequency = intList(R.string.set_key_backup_frequency)
                if (statsBackup.schedule.frequency == Frequency.OFF && frequency != null) {
                    frequency.setValueIndex(frequency.values.indexOf(Frequency.WEEKLY.code))
                } else {
                    statsBackup.reschedule()
                }
                updateBackupSummaries()
            }
        }

    private val restoreLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) confirmRestore(uri)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        intList(R.string.set_key_backup_frequency)?.apply {
            // The summary says when the next backup is, see updateBackupSummaries.
            summaryProvider = null
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val frequency = Frequency.of(newValue as Int)
                if (frequency != Frequency.OFF && statsBackup.folder == null) {
                    // A folder is needed first, the backup is turned on afterwards.
                    folderLauncher.launch(null)
                    false
                } else {
                    val wasOff = statsBackup.schedule.frequency == Frequency.OFF
                    changeSchedule { it.copy(frequency = frequency) }
                    // Turning it on makes a backup right away, so the file shows up.
                    if (wasOff && frequency != Frequency.OFF) statsBackup.backUpSoon()
                    true
                }
            }
        }
        intList(R.string.set_key_backup_weekday)?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                changeSchedule { it.copy(weekday = DayOfWeek.of(newValue as Int)) }
                true
            }
        intList(R.string.set_key_backup_day)?.apply {
            summaryProvider = null
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                changeSchedule { it.copy(dayOfMonth = newValue as Int) }
                true
            }
        }
        intList(R.string.set_key_backup_hour)?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                changeSchedule { it.copy(hour = newValue as Int) }
                true
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
        findPreference<Preference>(getString(R.string.set_key_backup_restore))
            ?.setOnPreferenceClickListener {
                restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                true
            }
        updateBackupSummaries()
    }

    /** Write a backup right away. */
    private fun backUpNow(force: Boolean = false) {
        val context = requireContext()
        if (statsBackup.folder == null) {
            folderLauncher.launch(null)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome =
                withContext(Dispatchers.IO) {
                    runCatching { statsBackup.run(force) }.getOrDefault(AppBackup.Outcome.Failed)
                }
            when (outcome) {
                is AppBackup.Outcome.Written ->
                    android.widget.Toast.makeText(
                            context,
                            getString(R.string.lng_stats_backup_done, outcome.name),
                            android.widget.Toast.LENGTH_SHORT,
                        )
                        .show()
                is AppBackup.Outcome.Suspicious -> confirmSmallBackup(outcome)
                else -> context.showToast(R.string.err_stats_backup)
            }
            updateBackupSummaries()
        }
    }

    /** A backup that holds much less than the last one is only written when the user insists. */
    private fun confirmSmallBackup(outcome: AppBackup.Outcome.Suspicious) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lbl_backup_small_title)
            .setMessage(
                getString(
                    R.string.fmt_backup_small_desc,
                    outcome.now.plays,
                    outcome.now.playlists,
                    outcome.last.plays,
                    outcome.last.playlists,
                )
            )
            .setPositiveButton(R.string.lbl_backup_small_ok) { _, _ -> backUpNow(force = true) }
            .setNegativeButton(R.string.lbl_cancel, null)
            .create()
            .apply { blackenWindowOnShow() }
            .show()
    }

    private fun confirmRestore(uri: Uri) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) { statsBackup.read(uri) }
            when {
                data == null -> context.showToast(R.string.err_backup_unreadable)
                data.info.format > BackupArchive.FORMAT ->
                    context.showToast(R.string.err_backup_newer)
                else -> askToRestore(data)
            }
        }
    }

    private fun askToRestore(data: BackupData) {
        val context = requireContext()
        val info = data.info
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.lbl_backup_restore_title)
            .setMessage(
                getString(
                    R.string.fmt_backup_restore_desc,
                    java.text.DateFormat.getDateInstance().format(java.util.Date(info.createdAt)),
                    info.appVersion,
                    info.plays,
                    info.playlists,
                    info.settings,
                )
            )
            .setPositiveButton(R.string.lbl_backup_restore_ok) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val restored = withContext(Dispatchers.IO) { statsBackup.restore(data) }
                    android.widget.Toast.makeText(
                            context,
                            getString(
                                R.string.fmt_backup_restored,
                                restored.plays,
                                restored.playlists,
                                restored.settings,
                            ),
                            android.widget.Toast.LENGTH_LONG,
                        )
                        .show()
                    if (restored.missingSongs > 0) {
                        android.widget.Toast.makeText(
                                context,
                                getString(R.string.fmt_backup_missing_songs, restored.missingSongs),
                                android.widget.Toast.LENGTH_LONG,
                            )
                            .show()
                    }
                    if (restored.libraryMissing) {
                        context.showToast(R.string.lng_backup_no_library)
                    }
                }
            }
            .setNegativeButton(R.string.lbl_cancel, null)
            .create()
            .apply { blackenWindowOnShow() }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // An older backup that was on is stored as a weekly one before the settings are shown,
        // and the plan is made again, so what the screen says is what will happen.
        statsBackup.schedule = statsBackup.schedule
        statsBackup.reschedule()
        super.onCreate(savedInstanceState)
    }

    private fun intList(key: Int) = findPreference<IntListPreference>(getString(key))

    /** Store a change of the schedule, plan the next backup after it and update the screen. */
    private fun changeSchedule(change: (BackupSchedule) -> BackupSchedule) {
        statsBackup.schedule = change(statsBackup.schedule)
        statsBackup.reschedule()
        updateBackupSummaries()
    }

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
        val schedule = statsBackup.schedule
        intList(R.string.set_key_backup_weekday)?.isVisible = schedule.frequency == Frequency.WEEKLY
        intList(R.string.set_key_backup_day)?.apply {
            isVisible = schedule.frequency == Frequency.MONTHLY
            // Only a day that not every month has needs a word on what happens then.
            summary =
                getString(
                    if (schedule.dayOfMonth > 28) R.string.set_backup_day_desc
                    else R.string.set_backup_day_plain,
                    schedule.dayOfMonth.toString(),
                )
        }
        intList(R.string.set_key_backup_hour)?.isVisible = schedule.frequency != Frequency.OFF
        val frequencyName =
            getString(
                when (schedule.frequency) {
                    Frequency.OFF -> R.string.set_backup_off
                    Frequency.DAILY -> R.string.set_backup_daily
                    Frequency.WEEKLY -> R.string.set_backup_weekly
                    Frequency.MONTHLY -> R.string.set_backup_monthly
                }
            )
        val next =
            statsBackup.nextRun()?.let {
                getString(
                    R.string.fmt_backup_next,
                    it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) +
                        " " +
                        it.format(DateTimeFormatter.ofPattern("HH:mm")),
                )
            }
        intList(R.string.set_key_backup_frequency)?.summary =
            listOfNotNull(
                    frequencyName,
                    if (statsBackup.lastVerdict != null)
                        getString(R.string.lbl_stats_backup_skipped)
                    else next,
                    if (schedule.frequency == Frequency.OFF) {
                        getString(R.string.set_backup_frequency_desc)
                    } else {
                        null
                    },
                )
                .joinToString("\n")
    }
}
