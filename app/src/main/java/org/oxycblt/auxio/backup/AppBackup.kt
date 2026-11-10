/*
 * Copyright (c) 2026 Auxio Project
 * AppBackup.kt is part of Auxio.
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
 
package org.oxycblt.auxio.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import org.oxycblt.auxio.BuildConfig
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.stats.PlayEvent
import org.oxycblt.auxio.stats.SafeDocumentWriter
import org.oxycblt.auxio.stats.StatsBackupWorker
import org.oxycblt.auxio.stats.StatsCsvImport
import org.oxycblt.auxio.stats.StatsRepository
import timber.log.Timber as L

/**
 * The full backup: the settings, the listening history and the playlists in one zip, written on a
 * [BackupSchedule] into a folder the user picked. Every backup has its own file
 * (`noxio_YYYY-MM-DD.zip`) and the last [BackupPolicy.KEEP] are kept. A backup that holds much less
 * than the one before is not written on its own, see [BackupPolicy.check].
 */
@Singleton
class AppBackup
internal constructor(
    private val context: Context,
    private val repository: StatsRepository,
    private val music: MusicRepository,
    private val openFolder: (Context, Uri) -> DocumentFile?,
    private val today: () -> LocalDate,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        repository: StatsRepository,
        music: MusicRepository,
    ) : this(
        context,
        repository,
        music,
        { c, uri -> DocumentFile.fromTreeUri(c, uri) },
        { LocalDate.now() },
    )

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    /** The folder the backups are written into, or null if none was chosen. */
    var folder: Uri?
        get() = sharedPreferences.getString(KEY_FOLDER, null)?.toUri()
        set(value) = sharedPreferences.edit { putString(KEY_FOLDER, value?.toString()) }

    /** When the last backup succeeded, in milliseconds since the unix epoch, or null. */
    val lastBackupMs: Long?
        get() = sharedPreferences.getLong(KEY_LAST, 0L).takeIf { it > 0 }

    /** What stopped the last automatic backup, or null if nothing did. */
    val lastVerdict: BackupPolicy.Verdict?
        get() =
            sharedPreferences.getString(KEY_VERDICT, null)?.let {
                runCatching { BackupPolicy.Verdict.valueOf(it) }.getOrNull()
            }

    /** The display name of the chosen folder, or null if there is none or it is unreachable. */
    val folderName: String?
        get() = folder?.let { openFolder(context, it)?.name }

    /** When the automatic backup runs. */
    var schedule: BackupSchedule
        get() {
            val frequency =
                if (sharedPreferences.contains(KEY_FREQUENCY)) {
                    BackupSchedule.Frequency.of(sharedPreferences.getInt(KEY_FREQUENCY, 0))
                } else if (sharedPreferences.getBoolean(OLD_KEY_SWITCH, false)) {
                    // The older backup could only be on, and then it ran once a week.
                    BackupSchedule.Frequency.WEEKLY
                } else {
                    BackupSchedule.Frequency.OFF
                }
            return BackupSchedule(
                frequency,
                DayOfWeek.of(sharedPreferences.getInt(KEY_WEEKDAY, 1).coerceIn(1, 7)),
                sharedPreferences.getInt(KEY_DAY, 1).coerceIn(1, 31),
                sharedPreferences.getInt(KEY_HOUR, BackupSchedule.DEFAULT_HOUR).coerceIn(0, 23),
            )
        }
        set(value) = sharedPreferences.edit {
            putInt(KEY_FREQUENCY, value.frequency.code)
            putInt(KEY_WEEKDAY, value.weekday.value)
            putInt(KEY_DAY, value.dayOfMonth)
            putInt(KEY_HOUR, value.hour)
            remove(OLD_KEY_SWITCH)
        }

    /** When the next automatic backup is due, or null if it is off. */
    fun nextRun(now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? = schedule.nextRun(now)

    /**
     * Plan the next automatic backup after the [schedule], or cancel it when the backup is off.
     *
     * @param fromWorker True when called by the backup job itself: the next run then waits for it
     *   to finish instead of replacing it.
     */
    fun reschedule(now: ZonedDateTime = ZonedDateTime.now(), fromWorker: Boolean = false) {
        val work = WorkManager.getInstance(context)
        val next = schedule.nextRun(now)
        if (next == null) {
            work.cancelUniqueWork(WORK_NAME)
        } else {
            work.enqueueUniqueWork(
                WORK_NAME,
                if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE
                else ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<StatsBackupWorker>()
                    .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                    .build(),
            )
        }
        // The weekly job of the older backup is replaced by the plan above.
        work.cancelUniqueWork(OLD_WORK_NAME)
    }

    /** Make one backup in the background right away, outside of the schedule. */
    fun backUpSoon() {
        WorkManager.getInstance(context)
            .enqueue(
                OneTimeWorkRequestBuilder<StatsBackupWorker>()
                    .setInputData(workDataOf(StatsBackupWorker.KEY_ONCE to true))
                    .build()
            )
    }

    sealed interface Outcome {
        /** The backup was written to the file called [name]. */
        data class Written(val name: String) : Outcome

        /** Nothing was written, because [now] looks wrong next to what the last backup held. */
        data class Suspicious(
            val verdict: BackupPolicy.Verdict,
            val now: BackupCounts,
            val last: BackupCounts,
        ) : Outcome

        /** There is no folder, or it cannot be written to. */
        data object NoFolder : Outcome

        /** The file could not be written. The backups that were there are untouched. */
        data object Failed : Outcome
    }

    /**
     * Write a backup now.
     *
     * @param force Write it although it holds much less than the last good backup. Such a backup
     *   gets its own name and is never counted as a good one.
     */
    suspend fun run(force: Boolean = false): Outcome {
        val tree = folder?.let { openFolder(context, it) }
        if (tree == null || !tree.canWrite()) {
            L.w("Backup folder is missing or not writable")
            return Outcome.NoFolder
        }
        val existing = tree.listFiles().filter { it.name?.let(BackupPolicy::parse) != null }
        val newestGood =
            existing
                .filter { BackupPolicy.parse(it.name!!)?.small == false }
                .maxByOrNull { BackupPolicy.parse(it.name!!)!!.date }
        val lastInfo = newestGood?.let { readInfo(it) }

        val data = collect(fallbackPlaylists = newestGood?.let { readPlaylists(it) })
        val now = BackupCounts(data.info.plays, data.info.playlists)
        val verdict =
            BackupPolicy.check(now, lastInfo?.let { BackupCounts(it.plays, it.playlists) })
        if (verdict != BackupPolicy.Verdict.OK && !force) {
            L.w("Backup skipped: $verdict")
            sharedPreferences.edit { putString(KEY_VERDICT, verdict.name) }
            return Outcome.Suspicious(
                verdict,
                now,
                BackupCounts(lastInfo?.plays ?: 0, lastInfo?.playlists ?: 0),
            )
        }

        val name = BackupPolicy.fileName(today(), small = verdict != BackupPolicy.Verdict.OK)
        // The archive is made first and written safely: a failure halfway must not damage the file
        // of the same day, see SafeDocumentWriter.
        val bytes = ByteArrayOutputStream().also { BackupArchive.write(data, it) }.toByteArray()
        if (!SafeDocumentWriter.write(context, tree, name, MIME_TYPE) { it.write(bytes) }) {
            L.w("Could not write the backup file")
            return Outcome.Failed
        }
        sharedPreferences.edit {
            putLong(KEY_LAST, System.currentTimeMillis())
            remove(KEY_VERDICT)
        }
        // Only now, with the new file safe, are old ones removed.
        val names = tree.listFiles().mapNotNull { it.name }
        for (old in BackupPolicy.toDelete(names)) {
            tree.findFile(old)?.delete()
        }
        L.d("Backup written: $name")
        return Outcome.Written(name)
    }

    /** Gather everything a backup holds. */
    private suspend fun collect(fallbackPlaylists: List<BackupPlaylist>?): BackupData {
        val history = ByteArrayOutputStream().also { repository.exportCsv(it) }.toByteArray()
        val plays = repository.summary(PlayEvent.BASELINE_TIME until Long.MAX_VALUE).plays
        // Without a loaded library the playlists cannot be read, the ones of the last backup are
        // carried over instead of being lost.
        val playlists = readLibraryPlaylists() ?: fallbackPlaylists ?: emptyList()
        val settings = settings()
        return BackupData(
            BackupInfo(
                format = BackupArchive.FORMAT,
                appVersion = BuildConfig.VERSION_NAME,
                createdAt = System.currentTimeMillis(),
                plays = plays,
                playlists = playlists.size,
                settings = settings.size,
            ),
            settings,
            history,
            playlists,
        )
    }

    private fun readLibraryPlaylists(): List<BackupPlaylist>? =
        music.library?.playlists?.map { playlist ->
            BackupPlaylist(
                playlist.name.resolve(context),
                playlist.songs.map { song ->
                    BackupSong(
                        song.path.components.toString(),
                        song.name.resolve(context),
                        song.artists.joinToString(PlayEvent.ARTIST_SEPARATOR) {
                            it.name.resolve(context)
                        },
                        song.album.name.resolve(context),
                    )
                },
            )
        }

    /** The settings of the user, which is everything in the preferences except what is local. */
    fun settings(): Map<String, Any> =
        sharedPreferences.all.entries
            .filter { (key, value) -> value != null && !isLocal(key) }
            .associate { (key, value) -> key to value!! }

    private fun readInfo(file: DocumentFile): BackupInfo? =
        try {
            context.contentResolver.openInputStream(file.uri)?.use(BackupArchive::readInfo)
        } catch (e: IOException) {
            null
        }

    private fun readPlaylists(file: DocumentFile): List<BackupPlaylist>? = read(file.uri)?.playlists

    /** Read a backup file. Null if it is unreadable. */
    fun read(uri: Uri): BackupData? =
        try {
            context.contentResolver.openInputStream(uri)?.use(BackupArchive::read)
        } catch (e: IOException) {
            null
        }

    /** What a restore did. */
    data class Restored(
        val plays: Int,
        val settings: Int,
        /** How many playlists were created. */
        val playlists: Int,
        /** How many songs of those playlists are not in the library. */
        val missingSongs: Int,
        /** Whether the playlists could not be restored because the library is not loaded. */
        val libraryMissing: Boolean,
    )

    /**
     * Put the content of a backup back: the settings replace the current ones, the plays are added
     * to the history (what is there already is skipped), and the playlists are created unless one
     * of that name exists.
     */
    suspend fun restore(data: BackupData): Restored {
        sharedPreferences.edit {
            for ((key, value) in data.settings) {
                if (isLocal(key)) continue
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                    is Set<*> -> putStringSet(key, value as Set<String>)
                }
            }
        }
        val plays =
            StatsCsvImport.parse(String(data.history))?.let { repository.importCsv(listOf(it)) }
                ?: 0

        val library = music.library
        var created = 0
        var missing = 0
        if (library != null && data.playlists.isNotEmpty()) {
            val byPath = library.songs.associateBy { it.path.components.toString() }
            val byName =
                library.songs.groupBy {
                    songIdentity(
                        it.name.resolve(context),
                        artistsOf(it),
                        it.album.name.resolve(context),
                    )
                }
            val known = library.playlists.map { it.name.resolve(context) }.toMutableSet()
            for (playlist in data.playlists) {
                if (!known.add(playlist.name)) continue
                val songs =
                    playlist.songs.mapNotNull { saved ->
                        (byPath[saved.path]
                                ?: byName[songIdentity(saved.title, saved.artist, saved.album)]
                                    ?.firstOrNull())
                            .also { if (it == null) missing++ }
                    }
                if (songs.isNotEmpty()) {
                    music.createPlaylist(playlist.name, songs)
                    created++
                }
            }
        }
        return Restored(
            plays = plays,
            settings = data.settings.keys.count { !isLocal(it) },
            playlists = created,
            missingSongs = missing,
            libraryMissing = library == null && data.playlists.isNotEmpty(),
        )
    }

    private fun artistsOf(song: org.oxycblt.musikr.Song) =
        song.artists.joinToString(PlayEvent.ARTIST_SEPARATOR) { it.name.resolve(context) }

    private fun songIdentity(title: String, artist: String, album: String) = "$title|$artist|$album"

    /** Settings that belong to this phone and are neither saved nor restored. */
    private fun isLocal(key: String) =
        key.startsWith("noxio_stats_backup") ||
            key.startsWith("noxio_backup_") ||
            key == "noxio_home_tabs_reset"

    companion object {
        const val MIME_TYPE = "application/zip"
        const val WORK_NAME = "noxio_backup"
        /** The weekly job of the older, stats only backup. */
        const val OLD_WORK_NAME = "noxio_stats_backup"
        // The folder of the older backup carries over.
        private const val KEY_FOLDER = "noxio_stats_backup_folder_uri"
        private const val KEY_LAST = "noxio_stats_backup_last"
        private const val KEY_VERDICT = "noxio_stats_backup_verdict"
        private const val OLD_KEY_SWITCH = "noxio_stats_backup"
        const val KEY_FREQUENCY = "noxio_backup_frequency"
        const val KEY_WEEKDAY = "noxio_backup_weekday"
        const val KEY_DAY = "noxio_backup_day"
        const val KEY_HOUR = "noxio_backup_hour"
    }
}
