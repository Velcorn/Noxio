/*
 * Copyright (c) 2026 Auxio Project
 * StatsBackup.kt is part of Auxio.
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
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber as L

/**
 * Keeps a CSV copy of the play history up to date in a folder the user picked, once a week. The
 * file is overwritten each time, so the folder always holds the latest full backup.
 */
@Singleton
class StatsBackup
internal constructor(
    private val context: Context,
    private val repository: StatsRepository,
    private val openFolder: (Context, Uri) -> DocumentFile?,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        repository: StatsRepository,
    ) : this(context, repository, { c, uri -> DocumentFile.fromTreeUri(c, uri) })

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    /** The folder the backup is written into, or null if none was chosen. */
    var folder: Uri?
        get() = sharedPreferences.getString(KEY_FOLDER, null)?.toUri()
        set(value) = sharedPreferences.edit { putString(KEY_FOLDER, value?.toString()) }

    /** When the last backup succeeded, in milliseconds since the unix epoch, or null. */
    val lastBackupMs: Long?
        get() = sharedPreferences.getLong(KEY_LAST, 0L).takeIf { it > 0 }

    /** The display name of the chosen folder, or null if there is none or it is unreachable. */
    val folderName: String?
        get() = folder?.let { openFolder(context, it)?.name }

    /** Start the weekly backup, and make one right away so the file shows up. */
    fun enable() {
        val work = WorkManager.getInstance(context)
        work.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StatsBackupWorker>(7, TimeUnit.DAYS).build(),
        )
        work.enqueue(OneTimeWorkRequestBuilder<StatsBackupWorker>().build())
    }

    fun disable() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * Write the backup now.
     *
     * @return False if there is no usable folder, true once the file was written.
     */
    suspend fun run(): Boolean {
        val tree = folder?.let { openFolder(context, it) }
        if (tree == null || !tree.canWrite()) {
            L.w("Stats backup folder is missing or not writable")
            return false
        }
        // The export is made first and written safely: a failure halfway must not damage the last
        // good backup. See SafeDocumentWriter.
        val csv = ByteArrayOutputStream().also { repository.exportCsv(it) }.toByteArray()
        val written =
            SafeDocumentWriter.write(context, tree, FILE_NAME, "text/csv") { it.write(csv) }
        if (!written) {
            L.w("Could not write the stats backup file")
            return false
        }
        sharedPreferences.edit { putLong(KEY_LAST, System.currentTimeMillis()) }
        L.d("Stats backup written")
        return true
    }

    companion object {
        const val FILE_NAME = "noxio-stats.csv"
        private const val WORK_NAME = "noxio_stats_backup"
        private const val KEY_FOLDER = "noxio_stats_backup_folder_uri"
        private const val KEY_LAST = "noxio_stats_backup_last"
    }
}

/** The weekly job that runs [StatsBackup]. */
class StatsBackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun statsBackup(): StatsBackup
    }

    override suspend fun doWork(): Result {
        val backup =
            EntryPointAccessors.fromApplication(applicationContext, Entry::class.java).statsBackup()
        return try {
            // Without a usable folder retrying will not help, the next weekly run will try again.
            if (backup.run()) Result.success() else Result.failure()
        } catch (e: Exception) {
            L.e("Stats backup failed: $e")
            Result.retry()
        }
    }
}
