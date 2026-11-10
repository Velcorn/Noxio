/*
 * Copyright (c) 2026 Auxio Project
 * StatsBackupWorker.kt is part of Auxio.
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
 
package org.oxycblt.auxio.stats

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.oxycblt.auxio.backup.AppBackup
import timber.log.Timber as L

/**
 * The job that runs [AppBackup] and plans the next run. It keeps the name it had when it only
 * backed up the stats once a week, so a job of the older backup that is still planned runs it, and
 * then moves on to the new plan.
 */
class StatsBackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun appBackup(): AppBackup
    }

    override suspend fun doWork(): Result {
        val backup =
            EntryPointAccessors.fromApplication(applicationContext, Entry::class.java).appBackup()
        try {
            backup.run()
        } catch (e: Exception) {
            L.e("Backup failed: $e")
        }
        // A backup that was asked for outside of the schedule does not plan the next one, that is
        // planned already. The job always succeeds, so the plan goes on after a failed backup.
        if (!inputData.getBoolean(KEY_ONCE, false)) {
            backup.reschedule(fromWorker = true)
        }
        return Result.success()
    }

    companion object {
        const val KEY_ONCE = "noxio_backup_once"
    }
}
