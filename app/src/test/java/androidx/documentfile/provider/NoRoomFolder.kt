/*
 * Copyright (c) 2026 Auxio Project
 * NoRoomFolder.kt is part of Auxio.
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
 
package androidx.documentfile.provider

/**
 * A folder that works like [inner] but cannot create any file. The constructor of [DocumentFile] is
 * only open to this package, which is why this test helper lives in it.
 */
class NoRoomFolder(private val inner: DocumentFile) : DocumentFile(null) {
    override fun createFile(mimeType: String, displayName: String): DocumentFile? = null

    override fun createDirectory(displayName: String) = inner.createDirectory(displayName)

    override fun getUri() = inner.uri

    override fun getName() = inner.name

    override fun getType() = inner.type

    override fun isDirectory() = inner.isDirectory

    override fun isFile() = inner.isFile

    override fun isVirtual() = inner.isVirtual

    override fun lastModified() = inner.lastModified()

    override fun length() = inner.length()

    override fun canRead() = inner.canRead()

    override fun canWrite() = inner.canWrite()

    override fun delete() = inner.delete()

    override fun exists() = inner.exists()

    override fun listFiles() = inner.listFiles()

    override fun renameTo(displayName: String) = inner.renameTo(displayName)
}
