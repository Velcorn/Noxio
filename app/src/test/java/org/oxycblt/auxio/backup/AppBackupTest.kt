/*
 * Copyright (c) 2026 Auxio Project
 * AppBackupTest.kt is part of Auxio.
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
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.stats.PlayEvent
import org.oxycblt.auxio.stats.StatsCsvImport
import org.oxycblt.auxio.stats.StatsRepository
import org.oxycblt.auxio.stats.summarize
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppBackupTest {
    private class FakeRepository : StatsRepository {
        var exports = 0
        var plays = 3
        val imported = mutableListOf<StatsCsvImport.Parsed>()

        override suspend fun record(event: PlayEvent) = 1L

        override suspend fun updateListened(id: Long, listenedMs: Long) {}

        override suspend fun summary(range: LongRange, unknownArtist: String) =
            summarize(
                List(plays) {
                    PlayEvent(
                        songUid = "u",
                        title = "t",
                        artists = "a",
                        album = "b",
                        startedAt = 1,
                        listenedMs = 1,
                        durationMs = 1,
                    )
                }
            )

        override suspend fun exportCsv(out: OutputStream) {
            exports += 1
            out.write(
                "FILE_PATH,TITLE,ARTIST,ALBUM,ALBUM_ARTIST,COMPOSER,GENRE,YEAR,DURATION_MS,PLAY_COUNT,PLAYED_AT,LISTENED_MS,ESTIMATED\n"
                    .toByteArray()
            )
        }

        override suspend fun importCsv(files: List<StatsCsvImport.Parsed>): Int {
            imported += files
            return files.size
        }
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = FakeRepository()
    private val music = mockk<MusicRepository>(relaxed = true)
    private var day = LocalDate.of(2026, 11, 9)

    @get:Rule val folder = TemporaryFolder()

    private val treeUri = Uri.parse("content://tree/backups")

    private fun backupInto(dir: File = folder.root) =
        AppBackup(context, repository, music, { _, _ -> DocumentFile.fromFile(dir) }, { day })
            .also { it.folder = treeUri }

    private fun files() = folder.root.list()!!.sorted()

    private fun clearPreferences() =
        PreferenceManager.getDefaultSharedPreferences(context).edit(commit = true) { clear() }

    @org.junit.Before fun setUp() = clearPreferences()

    @Test
    fun withoutAFolderThereIsNothingToWrite() = runBlocking {
        val backup = AppBackup(context, repository, music, { _, _ -> null }, { day })
        assertNull(backup.folder)
        assertNull(backup.folderName)
        assertEquals(AppBackup.Outcome.NoFolder, backup.run())
        assertEquals(0, repository.exports)
    }

    @Test
    fun theFolderIsRememberedAndCanBeForgotten() {
        val uri =
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments")
        val backup = AppBackup(context, repository, music, { _, _ -> null }, { day })
        backup.folder = uri
        assertEquals(uri, AppBackup(context, repository, music, { _, _ -> null }, { day }).folder)
        backup.folder = null
        assertNull(backup.folder)
    }

    @Test
    fun aBackupIsAZipNamedAfterTheDayWithEverythingInIt() = runBlocking {
        PreferenceManager.getDefaultSharedPreferences(context).edit(commit = true) {
            putString("some_setting", "value")
            putBoolean("flag", true)
        }
        val backup = backupInto()
        assertEquals(AppBackup.Outcome.Written("noxio_2026-11-09.zip"), backup.run())
        assertEquals(listOf("noxio_2026-11-09.zip"), files())
        assertNotNull(backup.lastBackupMs)

        val read = backup.read(Uri.fromFile(File(folder.root, "noxio_2026-11-09.zip")))!!
        assertEquals(3, read.info.plays)
        assertEquals("value", read.settings["some_setting"])
        assertEquals(true, read.settings["flag"])
    }

    @Test
    fun theSettingsOfThisPhoneAreNotSaved() = runBlocking {
        PreferenceManager.getDefaultSharedPreferences(context).edit(commit = true) {
            putString("noxio_stats_backup_folder_uri", "content://x")
            putLong("noxio_stats_backup_last", 5L)
            putBoolean("noxio_stats_backup", true)
            putBoolean("kept", true)
        }
        val backup = backupInto()
        assertEquals(setOf("kept"), backup.settings().keys)
    }

    @Test
    fun theBackupOfTheSameDayIsReplacedAndAnotherDayGetsItsOwnFile() = runBlocking {
        val backup = backupInto()
        backup.run()
        backup.run()
        assertEquals(listOf("noxio_2026-11-09.zip"), files())
        day = day.plusDays(7)
        backup.run()
        assertEquals(listOf("noxio_2026-11-09.zip", "noxio_2026-11-16.zip"), files())
    }

    @Test
    fun onlyTheLastFiveBackupsAreKept() = runBlocking {
        val backup = backupInto()
        repeat(8) {
            backup.run()
            day = day.plusDays(7)
        }
        assertEquals(5, files().size)
        assertEquals("noxio_2026-12-28.zip", files().last())
        assertFalse("noxio_2026-11-09.zip" in files())
    }

    @Test
    fun otherFilesInTheFolderAreLeftAlone() = runBlocking {
        File(folder.root, "noxio-stats.csv").writeText("old")
        File(folder.root, "notes.txt").writeText("mine")
        val backup = backupInto()
        repeat(7) {
            backup.run()
            day = day.plusDays(1)
        }
        assertTrue("noxio-stats.csv" in files())
        assertTrue("notes.txt" in files())
    }

    @Test
    fun anEmptyAppDoesNotReplaceAGoodBackup() = runBlocking {
        val backup = backupInto()
        assertTrue(backup.run() is AppBackup.Outcome.Written)
        val before = File(folder.root, "noxio_2026-11-09.zip").readBytes().size

        repository.plays = 0
        day = day.plusDays(7)
        val outcome = backup.run()
        assertTrue(outcome is AppBackup.Outcome.Suspicious)
        assertEquals(BackupPolicy.Verdict.EMPTY, (outcome as AppBackup.Outcome.Suspicious).verdict)
        assertEquals(listOf("noxio_2026-11-09.zip"), files())
        assertEquals(before, File(folder.root, "noxio_2026-11-09.zip").readBytes().size)
        assertEquals(BackupPolicy.Verdict.EMPTY, backup.lastVerdict)

        // A good run clears the note.
        repository.plays = 3
        backup.run()
        assertNull(backup.lastVerdict)
    }

    @Test
    fun aSmallBackupIsOnlyWrittenWhenForcedAndGetsItsOwnName() = runBlocking {
        val backup = backupInto()
        repository.plays = 100
        backup.run()
        repository.plays = 10
        day = day.plusDays(7)
        assertTrue(backup.run() is AppBackup.Outcome.Suspicious)
        assertEquals(
            AppBackup.Outcome.Written("noxio_2026-11-16_small.zip"),
            backup.run(force = true),
        )
        assertEquals(listOf("noxio_2026-11-09.zip", "noxio_2026-11-16_small.zip"), files())
    }

    @Test
    fun aSmallBackupDoesNotPushOutTheGoodOnes() = runBlocking {
        val backup = backupInto()
        repository.plays = 100
        repeat(5) {
            backup.run()
            day = day.plusDays(7)
        }
        repository.plays = 1
        repeat(4) {
            backup.run(force = true)
            day = day.plusDays(7)
        }
        val good = files().count { !it.contains("_small") }
        assertTrue("only $good good backups left", good >= BackupPolicy.MIN_GOOD)
    }

    @Test
    fun aFolderThatCannotBeWrittenToIsAFailure() = runBlocking {
        val readOnly = folder.newFolder().apply { setWritable(false) }
        val backup = backupInto(readOnly)
        // A user that can write anything (root on a build machine) cannot make a folder read only.
        if (!readOnly.canWrite()) {
            assertEquals(AppBackup.Outcome.NoFolder, backup.run())
            assertNull(backup.lastBackupMs)
        }
    }

    @Test
    fun theScheduleIsStoredAndOffAtFirst() {
        val backup = backupInto()
        assertEquals(BackupSchedule.Frequency.OFF, backup.schedule.frequency)
        val schedule =
            BackupSchedule(BackupSchedule.Frequency.MONTHLY, java.time.DayOfWeek.FRIDAY, 12, 21)
        backup.schedule = schedule
        assertEquals(schedule, backupInto().schedule)
    }

    @Test
    fun theOlderBackupThatWasOnBecomesAWeeklyOne() {
        PreferenceManager.getDefaultSharedPreferences(context).edit(commit = true) {
            putBoolean("noxio_stats_backup", true)
        }
        val backup = backupInto()
        assertEquals(BackupSchedule.Frequency.WEEKLY, backup.schedule.frequency)
        // Storing it once makes that stick, the old switch is gone.
        backup.schedule = backup.schedule
        assertFalse(
            PreferenceManager.getDefaultSharedPreferences(context).contains("noxio_stats_backup")
        )
        assertEquals(BackupSchedule.Frequency.WEEKLY, backupInto().schedule.frequency)
    }

    @Test
    fun theScheduleIsNotPartOfABackup() {
        val backup = backupInto()
        backup.schedule = BackupSchedule(BackupSchedule.Frequency.DAILY)
        assertTrue(backup.settings().keys.none { it.startsWith("noxio_backup_") })
    }

    @Test
    fun theNextBackupIsPlannedAndTurningItOffCancelsIt() {
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val work = androidx.work.WorkManager.getInstance(context)
        fun infos() = work.getWorkInfosForUniqueWork(AppBackup.WORK_NAME).get()
        val backup = backupInto()

        backup.schedule = BackupSchedule(BackupSchedule.Frequency.DAILY, hour = 3)
        val now = java.time.ZonedDateTime.of(2026, 11, 10, 13, 0, 0, 0, java.time.ZoneId.of("UTC"))
        backup.reschedule(now)
        val planned = infos().single()
        assertEquals(androidx.work.WorkInfo.State.ENQUEUED, planned.state)
        // Tomorrow at 3, fourteen hours from now.
        assertEquals(14 * 3600_000L, planned.initialDelayMillis)

        // Planning again replaces the plan instead of adding a second one.
        backup.reschedule(now)
        assertEquals(1, infos().count { it.state == androidx.work.WorkInfo.State.ENQUEUED })

        backup.schedule = BackupSchedule(BackupSchedule.Frequency.OFF)
        backup.reschedule(now)
        assertTrue(infos().all { it.state == androidx.work.WorkInfo.State.CANCELLED })
    }

    @Test
    fun planningCancelsTheWeeklyJobOfTheOlderBackup() {
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val work = androidx.work.WorkManager.getInstance(context)
        work.enqueueUniquePeriodicWork(
            AppBackup.OLD_WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            androidx.work
                .PeriodicWorkRequestBuilder<org.oxycblt.auxio.stats.StatsBackupWorker>(
                    7,
                    java.util.concurrent.TimeUnit.DAYS,
                )
                .build(),
        )
        val backup = backupInto()
        backup.schedule = BackupSchedule(BackupSchedule.Frequency.WEEKLY)
        backup.reschedule()
        assertTrue(
            work.getWorkInfosForUniqueWork(AppBackup.OLD_WORK_NAME).get().all {
                it.state == androidx.work.WorkInfo.State.CANCELLED
            }
        )
    }

    // --- restoring ---

    private fun data(settings: Map<String, Any>, playlists: List<BackupPlaylist> = emptyList()) =
        BackupData(
            BackupInfo(BackupArchive.FORMAT, "1.0.0", 0, 1, playlists.size, settings.size),
            settings,
            ("FILE_PATH,TITLE,ARTIST,ALBUM,ALBUM_ARTIST,COMPOSER,GENRE,YEAR,DURATION_MS,PLAY_COUNT,PLAYED_AT,LISTENED_MS,ESTIMATED\n" +
                    "a.mp3,T,A,B,<unknown>,<unknown>,<unknown>,0,5,1,2026-11-09T10:00:00Z,5,0\n")
                .toByteArray(),
            playlists,
        )

    @Test
    fun restoringPutsTheSettingsBackAndAddsThePlays() = runBlocking {
        val backup = backupInto()
        val restored =
            backup.restore(
                data(
                    mapOf(
                        "flag" to true,
                        "n" to 4,
                        "s" to "x",
                        "tags" to setOf("a"),
                        "noxio_stats_backup_folder_uri" to "content://other",
                    )
                )
            )
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        assertEquals(true, prefs.getBoolean("flag", false))
        assertEquals(4, prefs.getInt("n", 0))
        assertEquals("x", prefs.getString("s", null))
        assertEquals(setOf("a"), prefs.getStringSet("tags", null))
        // The folder of this phone is not overwritten by the one of the backup.
        assertEquals(treeUri.toString(), prefs.getString("noxio_stats_backup_folder_uri", null))
        assertEquals(4, restored.settings)
        assertEquals(1, restored.plays)
        assertEquals(1, repository.imported.size)
    }

    @Test
    fun playlistsAreNotRestoredWithoutALoadedLibrary() = runBlocking {
        val withoutLibrary = mockk<MusicRepository>(relaxed = true)
        io.mockk.every { withoutLibrary.library } returns null
        val backup =
            AppBackup(
                context,
                repository,
                withoutLibrary,
                { _, _ -> DocumentFile.fromFile(folder.root) },
                { day },
            )
        val restored =
            backup.restore(
                data(
                    emptyMap(),
                    listOf(BackupPlaylist("Gym", listOf(BackupSong("a.mp3", "T", "A", "B")))),
                )
            )
        assertTrue(restored.libraryMissing)
        assertEquals(0, restored.playlists)
    }

    // --- playlists from and into the library ---

    private fun name(raw: String) =
        mockk<org.oxycblt.musikr.tag.Name.Known> {
            io.mockk.every { this@mockk.raw } returns raw
        }

    private fun song(path: String, title: String, artist: String, album: String) =
        mockk<org.oxycblt.musikr.Song>(relaxed = true) {
            io.mockk.every { this@mockk.path } returns
                org.oxycblt.musikr.fs.Path(
                    mockk(relaxed = true),
                    org.oxycblt.musikr.fs.Components.parseUnix(path),
                )
            io.mockk.every { this@mockk.name } returns name(title)
            val credited =
                mockk<org.oxycblt.musikr.Artist> {
                    io.mockk.every { this@mockk.name } returns name(artist)
                }
            io.mockk.every { artists } returns listOf(credited)
            val onAlbum =
                mockk<org.oxycblt.musikr.Album> {
                    io.mockk.every { this@mockk.name } returns name(album)
                }
            io.mockk.every { this@mockk.album } returns onAlbum
        }

    private fun playlist(title: String, songs: List<org.oxycblt.musikr.Song>) =
        mockk<org.oxycblt.musikr.Playlist> {
            io.mockk.every { name } returns name(title)
            io.mockk.every { this@mockk.songs } returns songs
        }

    private fun libraryWith(
        songs: List<org.oxycblt.musikr.Song>,
        playlists: List<org.oxycblt.musikr.Playlist> = emptyList(),
    ): MusicRepository {
        val library =
            mockk<org.oxycblt.musikr.Library> {
                io.mockk.every { this@mockk.songs } returns songs
                io.mockk.every { this@mockk.playlists } returns playlists
            }
        return mockk(relaxed = true) { io.mockk.every { this@mockk.library } returns library }
    }

    @Test
    fun thePlaylistsOfTheLibraryAreInTheBackup() = runBlocking {
        val a = song("Music/a.mp3", "A", "X", "Al")
        val b = song("Music/b.mp3", "B", "Y", "Bl")
        val music = libraryWith(listOf(a, b), listOf(playlist("Gym", listOf(b, a))))
        val backup =
            AppBackup(
                    context,
                    repository,
                    music,
                    { _, _ -> DocumentFile.fromFile(folder.root) },
                    { day },
                )
                .also { it.folder = treeUri }
        backup.run()
        val read = backup.read(Uri.fromFile(File(folder.root, "noxio_2026-11-09.zip")))!!
        assertEquals(1, read.info.playlists)
        assertEquals(
            listOf(
                BackupPlaylist(
                    "Gym",
                    listOf(
                        BackupSong("Music/b.mp3", "B", "Y", "Bl"),
                        BackupSong("Music/a.mp3", "A", "X", "Al"),
                    ),
                )
            ),
            read.playlists,
        )
    }

    @Test
    fun withoutALibraryThePlaylistsOfTheLastBackupAreKept() = runBlocking {
        val a = song("Music/a.mp3", "A", "X", "Al")
        val first =
            AppBackup(
                    context,
                    repository,
                    libraryWith(listOf(a), listOf(playlist("Gym", listOf(a)))),
                    { _, _ -> DocumentFile.fromFile(folder.root) },
                    { day },
                )
                .also { it.folder = treeUri }
        first.run()
        val noLibrary = mockk<MusicRepository>(relaxed = true)
        io.mockk.every { noLibrary.library } returns null
        day = day.plusDays(7)
        val second =
            AppBackup(
                context,
                repository,
                noLibrary,
                { _, _ -> DocumentFile.fromFile(folder.root) },
                { day },
            )
        second.run()
        val read = second.read(Uri.fromFile(File(folder.root, "noxio_2026-11-16.zip")))!!
        assertEquals(listOf("Gym"), read.playlists.map { it.name })
    }

    @Test
    fun restoredPlaylistsFindTheirSongsByPathOrByName() = runBlocking {
        val moved = song("Other/a.mp3", "A", "X", "Al")
        val same = song("Music/b.mp3", "B", "Y", "Bl")
        val music = libraryWith(listOf(moved, same), listOf(playlist("Existing", emptyList())))
        val backup =
            AppBackup(
                context,
                repository,
                music,
                { _, _ -> DocumentFile.fromFile(folder.root) },
                { day },
            )
        val restored =
            backup.restore(
                data(
                    emptyMap(),
                    listOf(
                        BackupPlaylist(
                            "Night",
                            listOf(
                                // Found by its path.
                                BackupSong("Music/b.mp3", "B", "Y", "Bl"),
                                // Moved, so found by its title, artist and album.
                                BackupSong("Music/a.mp3", "A", "X", "Al"),
                                // Not in the library.
                                BackupSong("Music/c.mp3", "C", "Z", "Cl"),
                            ),
                        ),
                        // Already there, so it is not made a second time.
                        BackupPlaylist(
                            "Existing",
                            listOf(BackupSong("Music/b.mp3", "B", "Y", "Bl")),
                        ),
                        // None of its songs is there, so there is nothing to make.
                        BackupPlaylist("Gone", listOf(BackupSong("x.mp3", "X", "X", "X"))),
                    ),
                )
            )
        io.mockk.coVerify(exactly = 1) { music.createPlaylist("Night", listOf(same, moved)) }
        io.mockk.coVerify(exactly = 0) { music.createPlaylist("Existing", any()) }
        io.mockk.coVerify(exactly = 0) { music.createPlaylist("Gone", any()) }
        assertEquals(1, restored.playlists)
        assertEquals(2, restored.missingSongs)
        assertFalse(restored.libraryMissing)
    }

    @Test
    fun aFileThatIsNoBackupReadsAsNothing() {
        val file = File(folder.root, "photo.zip").apply { writeText("no zip") }
        assertNull(backupInto().read(Uri.fromFile(file)))
        assertNull(backupInto().read(Uri.fromFile(File(folder.root, "missing.zip"))))
    }

    @Test
    fun theNextRunFollowsTheSchedule() {
        val backup = backupInto()
        val now = java.time.ZonedDateTime.of(2026, 11, 10, 13, 0, 0, 0, java.time.ZoneId.of("UTC"))
        assertNull(backup.nextRun(now))
        backup.schedule = BackupSchedule(BackupSchedule.Frequency.DAILY, hour = 3)
        assertEquals(now.plusHours(14), backup.nextRun(now))
    }

    @Test
    fun aBackupRightAwayIsQueuedOnceWithoutPlanningTheNext() {
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val work = androidx.work.WorkManager.getInstance(context)
        backupInto().backUpSoon()
        val queued =
            work.getWorkInfosByTag(org.oxycblt.auxio.stats.StatsBackupWorker::class.java.name).get()
        assertEquals(1, queued.size)
        assertTrue(work.getWorkInfosForUniqueWork(AppBackup.WORK_NAME).get().isEmpty())
    }
}
