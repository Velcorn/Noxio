/*
 * Copyright (c) 2026 Auxio Project
 * StatsBackupTest.kt is part of Auxio.
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
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.OutputStream
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsBackupTest {
    private class FakeRepository : StatsRepository {
        var exports = 0
        var content = "history"

        override suspend fun record(event: PlayEvent) = 1L

        override suspend fun updateListened(id: Long, listenedMs: Long) {}

        override suspend fun summary(range: LongRange, unknownArtist: String) =
            summarize(emptyList())

        override suspend fun exportCsv(out: OutputStream) {
            exports += 1
            out.write(content.toByteArray())
        }

        override suspend fun importCsv(files: List<StatsCsvImport.Parsed>) = 0
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = FakeRepository()
    private val backup = StatsBackup(context, repository)

    @get:Rule val folder = TemporaryFolder()

    private val treeUri = Uri.parse("content://tree/backups")

    /** A backup whose folder is a real folder on disk. */
    private fun backupInto(dir: File = folder.root) =
        StatsBackup(context, repository) { _, _ -> DocumentFile.fromFile(dir) }
            .also { it.folder = treeUri }

    @Test
    fun theBackupFileIsAlwaysTheSameOne() {
        assertEquals("noxio-stats.csv", StatsBackup.FILE_NAME)
    }

    @Test
    fun withoutAFolderThereIsNothingToShowOrWrite() = runBlocking {
        assertNull(backup.folder)
        assertNull(backup.folderName)
        assertNull(backup.lastBackupMs)
        assertFalse(backup.run())
        // Nothing is exported when there is nowhere to write it.
        assertEquals(0, repository.exports)
    }

    @Test
    fun theFolderIsRememberedAndCanBeForgotten() {
        val uri =
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments")
        backup.folder = uri
        assertEquals(uri, StatsBackup(context, repository).folder)
        backup.folder = null
        assertNull(backup.folder)
    }

    @Test
    fun aBackupWritesTheHistoryIntoTheFolderAndRemembersWhen() = runBlocking {
        val backup = backupInto()
        assertTrue(backup.run())
        assertEquals(listOf(StatsBackup.FILE_NAME), folder.root.list()!!.toList())
        assertEquals(1, repository.exports)
        assertNotNull(backup.lastBackupMs)
        assertEquals(folder.root.name, backup.folderName)
    }

    @Test
    fun theNextBackupReplacesTheFileInsteadOfAddingAnother() = runBlocking {
        val backup = backupInto()
        repository.content = "first"
        assertTrue(backup.run())
        repository.content = "second, and longer"
        assertTrue(backup.run())
        assertEquals(listOf(StatsBackup.FILE_NAME), folder.root.list()!!.toList())
        assertEquals("second, and longer", File(folder.root, StatsBackup.FILE_NAME).readText())
        assertEquals(2, repository.exports)
    }

    @Test
    fun aFolderThatCannotBeWrittenToIsAFailure() = runBlocking {
        val readOnly = folder.newFolder().apply { setWritable(false) }
        val backup = backupInto(readOnly)
        // A user that can write anything (root on a build machine) cannot make a folder read only.
        if (!readOnly.canWrite()) {
            assertFalse(backup.run())
            assertNull(backup.lastBackupMs)
        }
    }

    @Test
    fun anUnreachableFolderIsAFailureAndLeavesNoTraceOfABackup() = runBlocking {
        backup.folder = Uri.parse("content://nobody/tree/missing")
        assertFalse(backup.run())
        assertNull(backup.lastBackupMs)
    }
}
