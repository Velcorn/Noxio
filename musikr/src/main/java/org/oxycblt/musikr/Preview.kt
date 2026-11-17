/*
 * Copyright (c) 2026 Auxio Project
 * Preview.kt is part of Auxio.
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
 
package org.oxycblt.musikr

import android.content.Context
import android.net.Uri
import org.oxycblt.musikr.cache.Cache
import org.oxycblt.musikr.cache.CacheResult
import org.oxycblt.musikr.covers.CoverResult
import org.oxycblt.musikr.covers.Covers
import org.oxycblt.musikr.fs.AddedMs
import org.oxycblt.musikr.fs.Components
import org.oxycblt.musikr.fs.File
import org.oxycblt.musikr.fs.Path
import org.oxycblt.musikr.fs.Volume
import org.oxycblt.musikr.fs.path.VolumeManager
import org.oxycblt.musikr.graph.MusicGraph
import org.oxycblt.musikr.model.LibraryFactory
import org.oxycblt.musikr.pipeline.RawSong
import org.oxycblt.musikr.playlist.db.StoredPlaylists
import org.oxycblt.musikr.playlist.interpret.PlaylistInterpreter
import org.oxycblt.musikr.tag.interpret.TagInterpreter

/**
 * Noxio: where a song's file is, enough to build the song again from the tag cache without looking
 * through the device. Saved with the queue, so it can play before the library has loaded.
 *
 * @param volume The MediaStore name of the volume, or the tree URI of a third-party location.
 */
data class SongFile(
    val uri: Uri,
    val volume: String,
    val path: String,
    val mimeType: String,
    val size: Long,
    val modifiedMs: Long,
) {
    companion object {
        private const val THIRD_PARTY = "uri:"

        /** Where [song]'s file is, or null if its volume cannot be named. */
        fun of(song: Song): SongFile? {
            val volume =
                when (val volume = song.path.volume) {
                    is Volume.ThirdParty -> THIRD_PARTY + volume.uri
                    else -> volume.mediaStoreName ?: return null
                }
            return SongFile(
                song.uri,
                volume,
                song.path.components.unixString,
                song.format.mimeType,
                song.size,
                song.modifiedMs,
            )
        }

        internal fun volumeOf(name: String, volumes: List<Volume>): Volume? =
            if (name.startsWith(THIRD_PARTY)) {
                Volume.ThirdParty(Uri.parse(name.removePrefix(THIRD_PARTY)))
            } else {
                volumes.firstOrNull { it.mediaStoreName == name }
            }
    }
}

/**
 * Noxio: a small library of only the given songs, built from the tag cache alone. It is meant to
 * stand in for the songs of a saved queue while the real library loads, and nothing more: songs
 * that are not in the cache, or changed since, are left out. The stored playlists are in it, with
 * only the songs that are.
 */
object Preview {
    private object NoAddedMs : AddedMs {
        override suspend fun resolve(): Long? = null
    }

    suspend fun library(
        context: Context,
        files: List<SongFile>,
        cache: Cache,
        covers: Covers<*>,
        storedPlaylists: StoredPlaylists,
        interpretation: Interpretation,
    ): Library {
        val volumes = VolumeManager.from(context).getVolumes()
        val interpreter = TagInterpreter.new(interpretation)
        val playlistInterpreter = PlaylistInterpreter.new(interpretation)
        val builder = MusicGraph.builder()
        for (saved in files.distinctBy { it.uri }) {
            val volume = SongFile.volumeOf(saved.volume, volumes) ?: continue
            val file =
                File(
                    saved.uri,
                    Path(volume, Components.parseUnix(saved.path)),
                    NoAddedMs,
                    saved.modifiedMs,
                    saved.mimeType,
                    saved.size,
                    null,
                )
            val cached = (cache.read(file) as? CacheResult.Hit)?.file ?: continue
            val audio = cached.audio ?: continue
            val cover =
                when (val result = audio.coverId?.let { covers.obtain(it) }) {
                    is CoverResult.Hit -> result.cover
                    else -> null
                }
            builder.add(
                interpreter.interpret(
                    RawSong(file, audio.properties, audio.tags, cover, cached.addedMs),
                ),
            )
        }
        // The playlists too, so a queue played from one still knows it. They only hold the
        // songs that are here.
        for (playlist in storedPlaylists.read()) {
            builder.add(playlistInterpreter.interpret(playlist))
        }
        return LibraryFactory.new().create(builder.build(), storedPlaylists, playlistInterpreter)
    }
}
