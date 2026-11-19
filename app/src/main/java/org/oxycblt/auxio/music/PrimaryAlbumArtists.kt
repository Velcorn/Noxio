/*
 * Copyright (c) 2026 Auxio Project
 * PrimaryAlbumArtists.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music

import android.content.Context
import androidx.preference.PreferenceManager
import org.oxycblt.auxio.R
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Song

/** A library view that credits each album and its songs to its first album artist. */
object PrimaryAlbumArtists {
    fun first(album: Album): Artist? = album.artists.firstOrNull()

    fun first(song: Song): Artist? = first(song.album)

    fun albums(artist: Artist, albums: Collection<Album>): List<Album> = albums.filter {
        first(it)?.uid == artist.uid
    }

    fun songs(artist: Artist, albums: Collection<Album>): List<Song> =
        albums(artist, albums).flatMap { it.songs }

    fun visible(artists: Collection<Artist>, albums: Collection<Album>): List<Artist> {
        val firstIds = albums.mapNotNullTo(mutableSetOf()) { first(it)?.uid }
        return artists.filter { it.uid in firstIds }
    }

    /**
     * The artists the Artists tab shows, in the given order: with [primaryOnly] only those first on
     * an album, otherwise without song-only artists when [hideCollaborators] is on. Search uses it
     * too, so it does not offer artists that have nothing to show.
     */
    fun shown(
        artists: List<Artist>,
        albums: Collection<Album>,
        primaryOnly: Boolean,
        hideCollaborators: Boolean,
    ): List<Artist> =
        when {
            primaryOnly -> visible(artists, albums)
            hideCollaborators -> artists.filter { it.explicitAlbums.isNotEmpty() }
            else -> artists
        }
}

fun Context.primaryAlbumArtistsOnly(): Boolean =
    PreferenceManager.getDefaultSharedPreferences(applicationContext)
        .getBoolean(getString(R.string.set_key_primary_album_artists), false)

fun Album.displayArtists(primaryOnly: Boolean): List<Artist> =
    if (primaryOnly) artists.take(1) else artists

fun Song.displayArtists(primaryOnly: Boolean): List<Artist> =
    if (primaryOnly) album.artists.take(1) else artists
