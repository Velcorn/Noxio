/*
 * Copyright (c) 2024 Auxio Project
 * HomeGenerator.kt is part of Auxio.
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
 
package org.oxycblt.auxio.home

import javax.inject.Inject
import org.oxycblt.auxio.home.tabs.Tab
import org.oxycblt.auxio.list.ListSettings
import org.oxycblt.auxio.list.adapter.UpdateInstructions
import org.oxycblt.auxio.list.sort.Sort
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.music.MusicType
import org.oxycblt.auxio.music.PrimaryAlbumArtists
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import timber.log.Timber as L

interface HomeGenerator {
    fun attach()

    fun release()

    fun empty(): Boolean

    fun songs(): List<Song>

    fun albums(): List<Album>

    fun artists(): List<Artist>

    fun genres(): List<Genre>

    fun playlists(): List<Playlist>

    fun tabs(): List<MusicType>

    interface Invalidator {
        fun invalidateEmpty() {}

        fun invalidateMusic(type: MusicType, instructions: UpdateInstructions)

        fun invalidateTabs()
    }

    interface Factory {
        fun create(invalidator: Invalidator): HomeGenerator
    }
}

class HomeGeneratorFactoryImpl
@Inject
constructor(
    private val homeSettings: HomeSettings,
    private val listSettings: ListSettings,
    private val musicRepository: MusicRepository,
) : HomeGenerator.Factory {
    override fun create(invalidator: HomeGenerator.Invalidator): HomeGenerator =
        HomeGeneratorImpl(invalidator, homeSettings, listSettings, musicRepository)
}

private class HomeGeneratorImpl(
    private val invalidator: HomeGenerator.Invalidator,
    private val homeSettings: HomeSettings,
    private val listSettings: ListSettings,
    private val musicRepository: MusicRepository,
) : HomeGenerator, HomeSettings.Listener, ListSettings.Listener, MusicRepository.UpdateListener {
    override fun attach() {
        homeSettings.registerListener(this)
        listSettings.registerListener(this)
        musicRepository.addUpdateListener(this)
    }

    override fun onTabsChanged() {
        invalidator.invalidateTabs()
    }

    override fun onHideCollaboratorsChanged() {
        // Changes in the hide collaborator setting will change the artist contents
        // of the library, consider it a library update.
        L.d("Collaborator setting changed, forwarding update")
        invalidator.invalidateMusic(MusicType.ARTISTS, UpdateInstructions.Diff)
    }

    override fun onPrimaryAlbumArtistsChanged() {
        invalidator.invalidateMusic(MusicType.ARTISTS, UpdateInstructions.Replace(0))
        invalidator.invalidateMusic(MusicType.ALBUMS, UpdateInstructions.Replace(0))
        invalidator.invalidateMusic(MusicType.SONGS, UpdateInstructions.Replace(0))
    }

    override fun onSongSortChanged() {
        super.onSongSortChanged()
        invalidator.invalidateMusic(MusicType.SONGS, UpdateInstructions.Replace(0))
    }

    override fun onAlbumSortChanged() {
        super.onAlbumSortChanged()
        invalidator.invalidateMusic(MusicType.ALBUMS, UpdateInstructions.Replace(0))
    }

    override fun onArtistSortChanged() {
        super.onArtistSortChanged()
        invalidator.invalidateMusic(MusicType.ARTISTS, UpdateInstructions.Replace(0))
    }

    override fun onGenreSortChanged() {
        super.onGenreSortChanged()
        invalidator.invalidateMusic(MusicType.GENRES, UpdateInstructions.Replace(0))
    }

    override fun onPlaylistSortChanged() {
        super.onPlaylistSortChanged()
        invalidator.invalidateMusic(MusicType.PLAYLISTS, UpdateInstructions.Replace(0))
    }

    override fun onMusicChanges(changes: MusicRepository.Changes) {
        invalidator.invalidateEmpty()

        val library = musicRepository.library
        if (changes.deviceLibrary && library != null) {
            L.d("Refreshing library")
            // Get the each list of items in the library to use as our list data.
            // Applying the preferred sorting to them.
            invalidator.invalidateMusic(MusicType.SONGS, UpdateInstructions.Diff)
            invalidator.invalidateMusic(MusicType.ALBUMS, UpdateInstructions.Diff)
            invalidator.invalidateMusic(MusicType.ARTISTS, UpdateInstructions.Diff)
            invalidator.invalidateMusic(MusicType.GENRES, UpdateInstructions.Diff)
        }

        if (changes.userLibrary && library != null) {
            L.d("Refreshing playlists")
            invalidator.invalidateMusic(MusicType.PLAYLISTS, UpdateInstructions.Diff)
        }
    }

    override fun release() {
        musicRepository.removeUpdateListener(this)
        listSettings.unregisterListener(this)
        homeSettings.unregisterListener(this)
    }

    override fun empty() = musicRepository.library?.empty() ?: true

    override fun songs() =
        musicRepository.library?.let {
            val sort = listSettings.songSort
            val sorted = sort.songs(it.songs)
            if (homeSettings.primaryAlbumArtistsOnly && sort.mode == Sort.Mode.ByArtist) {
                if (sort.direction == Sort.Direction.ASCENDING) {
                    sorted.sortedBy { song -> PrimaryAlbumArtists.first(song)?.name }
                } else {
                    sorted.sortedByDescending { song -> PrimaryAlbumArtists.first(song)?.name }
                }
            } else sorted
        } ?: emptyList()

    override fun albums() =
        musicRepository.library?.let {
            val sort = listSettings.albumSort
            val sorted = sort.albums(it.albums)
            if (homeSettings.primaryAlbumArtistsOnly && sort.mode == Sort.Mode.ByArtist) {
                if (sort.direction == Sort.Direction.ASCENDING) {
                    sorted.sortedBy { album -> PrimaryAlbumArtists.first(album)?.name }
                } else {
                    sorted.sortedByDescending { album -> PrimaryAlbumArtists.first(album)?.name }
                }
            } else sorted
        } ?: emptyList()

    override fun artists() =
        musicRepository.library?.let { deviceLibrary ->
            val sorted = listSettings.artistSort.artists(deviceLibrary.artists)
            if (homeSettings.primaryAlbumArtistsOnly) {
                val visible = PrimaryAlbumArtists.visible(sorted, deviceLibrary.albums)
                val sort = listSettings.artistSort
                val key: ((Artist) -> Long)? =
                    when (sort.mode) {
                        Sort.Mode.ByCount -> { artist ->
                            PrimaryAlbumArtists.songs(artist, artist.explicitAlbums).size.toLong()
                        }
                        Sort.Mode.ByDuration -> { artist ->
                            PrimaryAlbumArtists.songs(artist, artist.explicitAlbums).sumOf {
                                it.durationMs
                            }
                        }
                        else -> null
                    }
                if (key == null) visible
                else if (sort.direction == Sort.Direction.ASCENDING) visible.sortedBy(key)
                else visible.sortedByDescending(key)
            } else {
                PrimaryAlbumArtists.shown(
                    sorted,
                    deviceLibrary.albums,
                    false,
                    homeSettings.shouldHideCollaborators,
                )
            }
        } ?: emptyList()

    override fun genres() =
        musicRepository.library?.let { listSettings.genreSort.genres(it.genres) } ?: emptyList()

    override fun playlists() =
        musicRepository.library?.let { listSettings.playlistSort.playlists(it.playlists) }
            ?: emptyList()

    override fun tabs() = homeSettings.homeTabs.filterIsInstance<Tab.Visible>().map { it.type }
}
