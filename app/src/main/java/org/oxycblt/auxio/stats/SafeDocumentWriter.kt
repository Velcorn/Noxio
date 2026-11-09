/*
 * Copyright (c) 2026 Auxio Project
 * SafeDocumentWriter.kt is part of Auxio.
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
import java.io.IOException
import java.io.OutputStream
import timber.log.Timber as L

/**
 * Writes a file into a folder the user picked without a failure ever damaging the file that is
 * there. The new content goes into a temporary file first. Only when that is complete is it copied
 * over the real file, so:
 * - if producing the content fails, the old file is untouched, and
 * - if the copy fails, the temporary file still holds the complete new content.
 */
object SafeDocumentWriter {
    const val TEMP_SUFFIX = ".tmp"

    /**
     * @param folder The folder to write into.
     * @param name The name of the file.
     * @param mimeType The type of the file, used if it has to be created.
     * @param write Writes the content.
     * @return True if the file now holds the new content.
     */
    fun write(
        context: Context,
        folder: DocumentFile,
        name: String,
        mimeType: String,
        write: (OutputStream) -> Unit,
    ): Boolean {
        val resolver = context.contentResolver
        val tempName = name + TEMP_SUFFIX

        // A leftover from a run that was interrupted: it is stale, the content is made again.
        folder.findFile(tempName)?.delete()
        val temp = folder.createFile(TEMP_MIME_TYPE, tempName) ?: return false

        try {
            resolver.openOutputStream(temp.uri, "wt")?.use(write)
                ?: run {
                    temp.delete()
                    return false
                }
        } catch (e: IOException) {
            L.e("Could not write $tempName: $e")
            temp.delete()
            return false
        } catch (e: RuntimeException) {
            L.e("Could not produce the content of $name: $e")
            temp.delete()
            return false
        }

        val target = folder.findFile(name) ?: createNamed(folder, mimeType, name) ?: return false
        try {
            val input = resolver.openInputStream(temp.uri) ?: return false
            input.use {
                resolver.openOutputStream(target.uri, "wt")?.use { out -> it.copyTo(out) }
                    ?: return false
            }
        } catch (e: IOException) {
            // The temporary file is left behind on purpose, it holds the complete content.
            L.e("Could not copy $tempName over $name: $e")
            return false
        }
        temp.delete()
        return true
    }

    /**
     * Create a file with exactly [name]. Some providers add the extension of the type to the name
     * ("backup.csv" becomes "backup.csv.csv"), and the next run would then not find the file and
     * add another one. Such a file is renamed.
     */
    private fun createNamed(folder: DocumentFile, mimeType: String, name: String): DocumentFile? {
        val created = folder.createFile(mimeType, name) ?: return null
        if (created.name != name) {
            L.w("The folder named the new file ${created.name}, renaming it to $name")
            if (!created.renameTo(name)) {
                created.delete()
                return null
            }
            // renameTo can leave this instance pointing at the old name.
            return folder.findFile(name) ?: created
        }
        return created
    }

    /** A type nobody maps to an extension, so the name of the temporary file stays as it is. */
    private const val TEMP_MIME_TYPE = "application/x-noxio-temp"
}
