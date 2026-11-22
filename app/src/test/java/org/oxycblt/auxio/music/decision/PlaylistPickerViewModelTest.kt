/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistPickerViewModelTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.decision

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.music.PlaylistDecision
import org.oxycblt.musikr.Library
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.playlist.ExportConfig
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistPickerViewModelTest {
    private val songUid = Music.UID.fromString("uas00000000-0000-0000-0000-000000000001")!!
    private val playlistUid = mockk<Music.UID>()
    private val song = mockk<Song>(relaxed = true) { every { uid } returns songUid }
    private val playlist =
        mockk<Playlist>(relaxed = true) {
            every { uid } returns playlistUid
        }
    private var foundSong: Song? = song
    private var foundPlaylist: Playlist? = playlist
    private val library =
        mockk<Library> {
            every { findSong(songUid) } answers { foundSong }
            every { findPlaylist(playlistUid) } answers { foundPlaylist }
            every { findPlaylistByName(any()) } returns null
            every { playlists } returns emptyList()
        }
    private val repository =
        mockk<MusicRepository>(relaxed = true) {
            every { library } returns this@PlaylistPickerViewModelTest.library
        }
    private val model = PlaylistPickerViewModel(repository)

    @Test
    fun reopeningTheSameRequestsKeepsTheirPendingObjectsAndExportOptions() {
        val context =
            mockk<Context> {
                every { getString(R.string.fmt_def_playlist, 1) } returnsMany
                    listOf("Playlist 1", "Playlist 2")
            }
        model.setPendingPlaylist(context, arrayOf(songUid), null, PlaylistDecision.New.Reason.NEW)
        val pending = model.currentPendingNewPlaylist.value
        model.updateChosenName("My playlist")
        model.setPendingPlaylist(context, arrayOf(songUid), null, PlaylistDecision.New.Reason.NEW)
        assertSame(pending, model.currentPendingNewPlaylist.value)
        assertEquals(ChosenName.Valid("My playlist"), model.chosenName.value)

        model.setPlaylistToRename(
            playlistUid,
            arrayOf(songUid),
            null,
            PlaylistDecision.Rename.Reason.ACTION,
        )
        val rename = model.currentPendingRenamePlaylist.value
        model.setPlaylistToRename(
            playlistUid,
            arrayOf(songUid),
            null,
            PlaylistDecision.Rename.Reason.ACTION,
        )
        assertSame(rename, model.currentPendingRenamePlaylist.value)
        model.setSongsToAdd(arrayOf(songUid))
        val songs = model.currentSongsToAdd.value
        model.setSongsToAdd(arrayOf(songUid))
        assertSame(songs, model.currentSongsToAdd.value)
        model.setPlaylistToDelete(playlistUid)
        model.setPlaylistToExport(playlistUid)
        model.setExportConfig(ExportConfig(absolute = true, windowsPaths = true))
        foundPlaylist = mockk(relaxed = true) { every { uid } returns playlistUid }
        model.setPlaylistToRename(
            playlistUid,
            arrayOf(songUid),
            null,
            PlaylistDecision.Rename.Reason.ACTION,
        )
        assertSame(rename, model.currentPendingRenamePlaylist.value)
        model.setPlaylistToDelete(playlistUid)
        model.setPlaylistToExport(playlistUid)
        assertSame(playlist, model.currentPlaylistToDelete.value)
        assertSame(playlist, model.currentPlaylistToExport.value)
        assertEquals(
            ExportConfig(absolute = true, windowsPaths = true),
            model.currentExportConfig.value,
        )
    }

    @Test
    fun libraryRefreshUpdatesAllPendingReferencesAndDropsRemovedSongs() {
        model.setPlaylistToRename(
            playlistUid,
            arrayOf(songUid),
            "Imported",
            PlaylistDecision.Rename.Reason.IMPORT,
        )
        model.setPlaylistToDelete(playlistUid)
        model.setPlaylistToExport(playlistUid)
        val replacement = mockk<Playlist>(relaxed = true) { every { uid } returns playlistUid }
        val replacementSong = mockk<Song>(relaxed = true) { every { uid } returns songUid }
        foundPlaylist = replacement
        foundSong = replacementSong
        model.onMusicChanges(MusicRepository.Changes(deviceLibrary = true, userLibrary = false))
        assertSame(replacement, model.currentPendingRenamePlaylist.value!!.playlist)
        assertSame(replacementSong, model.currentPendingRenamePlaylist.value!!.applySongs.single())
        assertEquals("Imported", model.currentPendingRenamePlaylist.value!!.template)
        assertSame(replacement, model.currentPlaylistToDelete.value)
        assertSame(replacement, model.currentPlaylistToExport.value)
        foundSong = null
        model.onMusicChanges(MusicRepository.Changes(deviceLibrary = true, userLibrary = false))
        assertEquals(emptyList<Song>(), model.currentPendingRenamePlaylist.value!!.applySongs)
        foundPlaylist = null
        model.onMusicChanges(MusicRepository.Changes(deviceLibrary = false, userLibrary = true))
        assertNull(model.currentPendingRenamePlaylist.value)
        assertNull(model.currentPlaylistToDelete.value)
        assertNull(model.currentPlaylistToExport.value)
    }
}
