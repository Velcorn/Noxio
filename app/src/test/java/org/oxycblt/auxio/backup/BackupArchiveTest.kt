/*
 * Copyright (c) 2026 Auxio Project
 * BackupArchiveTest.kt is part of Auxio.
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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupArchiveTest {
    private val data =
        BackupData(
            BackupInfo(BackupArchive.FORMAT, "1.0.0", 1_700_000_000_000, 42, 2, 6),
            mapOf(
                "flag" to true,
                "count" to 7,
                "big" to 8_000_000_000L,
                "ratio" to 0.5f,
                "text" to "Dark, with \"quotes\"",
                "set" to setOf("a", "b"),
            ),
            "FILE_PATH,TITLE\nMusic/a.mp3,Ä\n".toByteArray(),
            listOf(
                BackupPlaylist(
                    "Gym",
                    listOf(BackupSong("Music/a.mp3", "Song, A", "Artist; B", "Album")),
                ),
                BackupPlaylist("Empty", emptyList()),
            ),
        )

    private fun bytes(data: BackupData) =
        ByteArrayOutputStream().also { BackupArchive.write(data, it) }.toByteArray()

    @Test
    fun aBackupIsReadBackAsItWasWritten() {
        val read = BackupArchive.read(ByteArrayInputStream(bytes(data)))!!
        assertEquals(data.info, read.info)
        assertEquals(data.settings, read.settings)
        assertArrayEquals(data.history, read.history)
        assertEquals(data.playlists, read.playlists)
    }

    @Test
    fun theInfoCanBeReadOnItsOwn() {
        assertEquals(data.info, BackupArchive.readInfo(ByteArrayInputStream(bytes(data))))
    }

    @Test
    fun somethingThatIsNoBackupIsNull() {
        assertNull(BackupArchive.read(ByteArrayInputStream("hello".toByteArray())))
        assertNull(BackupArchive.read(ByteArrayInputStream(ByteArray(0))))
        assertNull(BackupArchive.readInfo(ByteArrayInputStream("hello".toByteArray())))
    }

    @Test
    fun aZipWithoutTheBackupFilesIsNull() {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use {
            it.putNextEntry(java.util.zip.ZipEntry("photo.jpg"))
            it.write(1)
            it.closeEntry()
        }
        assertNull(BackupArchive.read(ByteArrayInputStream(out.toByteArray())))
    }

    @Test
    fun anEmptyBackupWorks() {
        val empty =
            BackupData(
                BackupInfo(BackupArchive.FORMAT, "1.0.0", 0, 0, 0, 0),
                emptyMap(),
                ByteArray(0),
                emptyList(),
            )
        assertEquals(empty.info, BackupArchive.read(ByteArrayInputStream(bytes(empty)))!!.info)
    }
}
