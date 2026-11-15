/*
 * Copyright (c) 2026 Auxio Project
 * TagEditor.kt is part of Auxio.
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
 
package org.oxycblt.auxio.tags

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.metadata.TagWriter
import timber.log.Timber as L

/** What came of writing tags into a file. */
sealed interface TagWriteResult {
    data object Saved : TagWriteResult

    /** Android has to ask the user first, see [TagEditor.writeRequest]. */
    data object NeedsPermission : TagWriteResult

    data object Failed : TagWriteResult
}

/**
 * Noxio: writes tags into the user's audio files. Android 11 and newer ask the user once for a set
 * of files ([writeRequest]); older versions need the storage permission ([permission]).
 */
@Singleton
class TagEditor
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Load the library again once Android has read the changed files at [uris], in the background,
     * so it can be called from a screen that closes.
     */
    fun reloadAfter(uris: Collection<Uri>) {
        scope.launch {
            refresh(uris)
            musicRepository.requestIndex(true)
        }
    }

    /** Write [fields] (see [TagWriter.write]) into the file of [song]. */
    suspend fun write(song: Song, fields: Map<String, List<String>>): TagWriteResult =
        write(song.uri, song.path.name?.substringAfterLast('.', "") ?: "", fields)

    /** Write [fields] into the file at [uri], whose name ends with [extension]. */
    suspend fun write(
        uri: Uri,
        extension: String,
        fields: Map<String, List<String>>,
    ): TagWriteResult =
        withContext(Dispatchers.IO) {
            val file =
                try {
                    context.contentResolver.openFileDescriptor(uri, "rw")
                } catch (e: SecurityException) {
                    L.d("No write access to $uri: $e")
                    return@withContext TagWriteResult.NeedsPermission
                } catch (e: IOException) {
                    L.w("Could not open $uri for writing: $e")
                    null
                } catch (e: IllegalArgumentException) {
                    L.w("Could not open $uri for writing: $e")
                    null
                } ?: return@withContext TagWriteResult.Failed
            if (TagWriter.write(file, extension, fields)) TagWriteResult.Saved
            else TagWriteResult.Failed
        }

    /**
     * Have Android read the changed files at [uris] again and wait for it, so that loading the
     * library afterwards sees the new tags (MediaStore tells the library when a file changed).
     */
    suspend fun refresh(uris: Collection<Uri>) {
        val paths = withContext(Dispatchers.IO) { uris.mapNotNull(::pathOf) }
        if (paths.isEmpty()) return
        // A big batch takes Android a while; it is waited for, but not forever.
        withTimeoutOrNull(SCAN_TIMEOUT_MS + paths.size * SCAN_TIMEOUT_PER_FILE_MS) {
            suspendCancellableCoroutine { continuation ->
                val left = AtomicInteger(paths.size)
                MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                    if (left.decrementAndGet() == 0 && continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
        }
    }

    /** Where the file at a MediaStore [uri] is, or null for other files. */
    @Suppress("DEPRECATION")
    private fun pathOf(uri: Uri): String? {
        if (uri.authority != MediaStore.AUTHORITY) return null
        return try {
            context.contentResolver
                .query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The system dialog that asks the user to let Noxio change the files at [uris], or null if no
     * dialog is needed or possible (folders picked in Noxio already allow writing; before Android
     * 11 the [permission] is asked instead).
     */
    fun writeRequest(uris: Collection<Uri>): IntentSender? {
        val media = uris.filter { it.authority == MediaStore.AUTHORITY }
        if (media.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return MediaStore.createWriteRequest(context.contentResolver, media).intentSender
    }

    /** Android 10 asks for one file at a time when writing fails: the dialog for [uri], or null. */
    fun recoverableRequest(uri: Uri): IntentSender? {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) return null
        return try {
            context.contentResolver.openFileDescriptor(uri, "rw")?.close()
            null
        } catch (e: RecoverableSecurityException) {
            e.userAction.actionIntent.intentSender
        } catch (e: Exception) {
            null
        }
    }

    /** The permission needed before Android 10 to write, or null if it is granted or not used. */
    fun permission(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return null
        val name = Manifest.permission.WRITE_EXTERNAL_STORAGE
        return name.takeIf {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 30_000L
        const val SCAN_TIMEOUT_PER_FILE_MS = 100L
    }
}
