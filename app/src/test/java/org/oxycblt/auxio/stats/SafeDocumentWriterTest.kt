/*
 * Copyright (c) 2026 Auxio Project
 * SafeDocumentWriterTest.kt is part of Auxio.
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
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafeDocumentWriterTest {
    @get:Rule val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "backup.dat"
    // A type without an extension, which a plain folder would otherwise add to the name.
    private val mime = "application/x-noxio-test"

    private fun dir() = DocumentFile.fromFile(folder.root)

    private fun file(fileName: String) = File(folder.root, fileName)

    private fun write(text: String) =
        SafeDocumentWriter.write(context, dir(), name, mime) { it.write(text.toByteArray()) }

    @Test
    fun aFolderThatAddsAnExtensionStillEndsUpWithTheExactNameOnceOnly() {
        val csv = "backup.csv"
        // A plain folder adds ".csv" to a name for this type, as some providers do.
        fun backup(text: String) =
            SafeDocumentWriter.write(context, dir(), csv, "text/csv") {
                it.write(text.toByteArray())
            }

        assertTrue(backup("first"))
        assertTrue(backup("second"))
        assertEquals(listOf(csv), folder.root.list()!!.toList())
        assertEquals("second", file(csv).readText())
    }

    @Test
    fun aNewFileIsCreatedWithTheContent() {
        assertTrue(write("hello"))
        assertEquals("hello", file(name).readText())
    }

    @Test
    fun nothingIsLeftBehindAfterASuccessfulWrite() {
        write("hello")
        assertEquals(listOf(name), folder.root.list()!!.toList())
    }

    @Test
    fun anOldFileIsReplacedByTheNewContent() {
        write("a much longer first backup")
        assertTrue(write("short"))
        // The old content is not left behind at the end of a shorter file.
        assertEquals("short", file(name).readText())
    }

    @Test
    fun aFailureWhileProducingTheContentLeavesTheOldBackupAlone() {
        write("the last good backup")

        val written =
            SafeDocumentWriter.write(context, dir(), name, mime) {
                it.write("half of the new ".toByteArray())
                throw IOException("disk full")
            }

        assertFalse(written)
        assertEquals("the last good backup", file(name).readText())
        assertFalse(file(name + SafeDocumentWriter.TEMP_SUFFIX).exists())
    }

    @Test
    fun anErrorWhileMakingTheContentAlsoLeavesTheOldBackupAlone() {
        write("the last good backup")

        val written =
            SafeDocumentWriter.write(context, dir(), name, mime) {
                throw IllegalStateException("could not read the database")
            }

        assertFalse(written)
        assertEquals("the last good backup", file(name).readText())
    }

    @Test
    fun aStaleTemporaryFileFromAnInterruptedRunIsReplaced() {
        file(name + SafeDocumentWriter.TEMP_SUFFIX).writeText("junk from an interrupted run")
        assertTrue(write("fresh"))
        assertEquals("fresh", file(name).readText())
        assertFalse(file(name + SafeDocumentWriter.TEMP_SUFFIX).exists())
    }
}
