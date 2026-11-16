/*
 * Copyright (c) 2026 Auxio Project
 * TagEditMigration.kt is part of Auxio.
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

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.musikr.Library
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.fs.Path
import org.oxycblt.musikr.playlist.db.StoredPlaylists

/**
 * Noxio: a song without a MusicBrainz ID gets its UID from its tags, so editing them gives it a new
 * one. Playlists point at songs by UID; after the library has loaded the edited files again, they
 * are moved over to the new UIDs. (The listening stats keep names, and the queue finds songs by
 * their file.)
 */
@Singleton
class TagEditMigration
@Inject
constructor(
    private val musicRepository: MusicRepository,
    private val storedPlaylists: StoredPlaylists,
) : MusicRepository.UpdateListener {
    private val pending = ConcurrentHashMap<Path, Edited>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listening = false

    /** Call before writing the tags of [song], on the main thread. */
    fun expect(song: Song) {
        pending[song.path] = Edited(song.uid, song.modifiedMs)
        if (!listening) {
            listening = true
            musicRepository.addUpdateListener(this)
        }
    }

    override fun onMusicChanges(changes: MusicRepository.Changes) {
        if (!changes.deviceLibrary) return
        val library = musicRepository.library ?: return
        val renamed = resolve(library)
        if (renamed.isEmpty()) return
        scope.launch {
            // The playlists are read with the library, so it is loaded once more to show them.
            if (storedPlaylists.replaceSongs(renamed)) musicRepository.requestIndex(true)
        }
    }

    /** The UIDs that changed, of the edited songs this [library] has loaded again. */
    private fun resolve(library: Library): Map<Music.UID, Music.UID> =
        renames(pending.toMap()) { library.findSongByPath(it)?.let { s -> s.uid to s.modifiedMs } }
            .also { (done, _) -> done.forEach(pending::remove) }
            .second
}

/** A song whose file is being written: its UID before, and when the file was changed. */
internal data class Edited(val uid: Music.UID, val modifiedMs: Long)

/**
 * Of the [edited] files, those that [find] shows changed: which are done, and the old to new UIDs
 * among them. A file that was not written yet (same time) waits.
 */
internal fun renames(
    edited: Map<Path, Edited>,
    find: (Path) -> Pair<Music.UID, Long>?,
): Pair<List<Path>, Map<Music.UID, Music.UID>> {
    val done = mutableListOf<Path>()
    val renamed = HashMap<Music.UID, Music.UID>()
    for ((path, before) in edited) {
        val (uid, modifiedMs) = find(path) ?: continue
        if (modifiedMs == before.modifiedMs) continue
        done.add(path)
        if (uid != before.uid) renamed[before.uid] = uid
    }
    return done to renamed
}
