/*
 * Copyright (c) 2026 Auxio Project
 * ListSettingsTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.list

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.R
import org.oxycblt.auxio.list.sort.Sort
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The order of every list is remembered, and falls back to its default. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = ListSettingsImpl(context)

    private val byName = Sort(Sort.Mode.ByName, Sort.Direction.ASCENDING)
    private val byDateOldestFirst = Sort(Sort.Mode.ByDate, Sort.Direction.ASCENDING)
    private val byDateNewestFirst = Sort(Sort.Mode.ByDate, Sort.Direction.DESCENDING)
    // Offered on every page that has a choice.
    private val custom = Sort(Sort.Mode.ByName, Sort.Direction.DESCENDING)
    private val removed = Sort(Sort.Mode.ByDuration, Sort.Direction.DESCENDING)

    /** Every sort, with the setting key it is stored under and its default. */
    private class Entry(
        val name: String,
        val key: Int,
        val default: Sort,
        val get: (ListSettings) -> Sort,
        val set: (ListSettings, Sort) -> Unit,
    ) {
        /** The lists of the home screen have no sort to choose, they are always by name. */
        val fixed: Boolean
            get() =
                name in listOf("songs", "albums", "artists", "genres", "playlists", "artist songs")
    }

    private val entries =
        listOf(
            Entry(
                "songs",
                R.string.set_key_songs_sort,
                byName,
                { it.songSort },
                { s, v -> s.songSort = v },
            ),
            Entry(
                "albums",
                R.string.set_key_albums_sort,
                byName,
                { it.albumSort },
                { s, v -> s.albumSort = v },
            ),
            Entry(
                "artists",
                R.string.set_key_artists_sort,
                byName,
                { it.artistSort },
                { s, v -> s.artistSort = v },
            ),
            Entry(
                "genres",
                R.string.set_key_genres_sort,
                byName,
                { it.genreSort },
                { s, v -> s.genreSort = v },
            ),
            Entry(
                "playlists",
                R.string.set_key_playlists_sort,
                byName,
                { it.playlistSort },
                { s, v -> s.playlistSort = v },
            ),
            Entry(
                "album songs",
                R.string.set_key_album_songs_sort,
                Sort(Sort.Mode.ByDisc, Sort.Direction.ASCENDING),
                { it.albumSongSort },
                { s, v -> s.albumSongSort = v },
            ),
            Entry(
                "artist songs",
                R.string.set_key_artist_songs_sort,
                byName,
                { it.artistSongSort },
                { s, v -> s.artistSongSort = v },
            ),
            Entry(
                "artist albums",
                R.string.set_key_artist_albums_sort,
                byDateNewestFirst,
                { it.artistAlbumSort },
                { s, v -> s.artistAlbumSort = v },
            ),
            Entry(
                "genre songs",
                R.string.set_key_genre_songs_sort,
                byName,
                { it.genreSongSort },
                { s, v -> s.genreSongSort = v },
            ),
        )

    @Test
    fun everyListStartsWithItsDefaultOrder() {
        for (e in entries) {
            assertEquals(e.name, e.default, e.get(settings))
        }
    }

    @Test
    fun everyPageListRemembersItsOrder() {
        for (e in entries.filterNot { it.fixed }) {
            e.set(settings, custom)
            assertEquals(e.name, custom, e.get(ListSettingsImpl(context)))
        }
    }

    @Test
    fun theHomeListsStayByNameAlsoWithAStoredOrder() {
        for (e in entries.filter { it.fixed }) {
            e.set(settings, custom)
            assertEquals(e.name, byName, e.get(ListSettingsImpl(context)))
        }
    }

    @Test
    fun theListsDoNotShareTheirOrder() {
        settings.songSort = custom
        for (e in entries.drop(1)) {
            assertEquals(e.name, e.default, e.get(settings))
        }
    }

    @Test
    fun anOrderThatCannotBeReadFallsBackToTheDefault() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        for (e in entries) {
            preferences.edit { putInt(context.getString(e.key), 0x7FFF_FFF0) }
            assertEquals(e.name, e.default, e.get(settings))
        }
    }

    @Test
    fun removedModesFallBackAndAllowedModesAreKept() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit {
            putInt(context.getString(R.string.set_key_artist_albums_sort), removed.intCode)
            putInt(
                context.getString(R.string.set_key_album_songs_sort),
                Sort(Sort.Mode.ByTrack, Sort.Direction.ASCENDING).intCode,
            )
        }
        assertEquals(byDateNewestFirst, settings.artistAlbumSort)
        assertEquals(Sort(Sort.Mode.ByDisc, Sort.Direction.ASCENDING), settings.albumSongSort)

        preferences.edit {
            putInt(
                context.getString(R.string.set_key_artist_albums_sort),
                byDateOldestFirst.intCode,
            )
            putInt(context.getString(R.string.set_key_album_songs_sort), byName.intCode)
        }
        assertEquals(byDateOldestFirst, settings.artistAlbumSort)
        assertEquals(byName, settings.albumSongSort)
    }

    @Test
    fun aChangeIsReportedToTheListenerOfThatList() {
        val reported = mutableListOf<String>()
        val listener =
            object : ListSettings.Listener {
                override fun onSongSortChanged() {
                    reported += "songs"
                }

                override fun onAlbumSortChanged() {
                    reported += "albums"
                }

                override fun onAlbumSongSortChanged() {
                    reported += "album songs"
                }

                override fun onArtistSortChanged() {
                    reported += "artists"
                }

                override fun onArtistSongSortChanged() {
                    reported += "artist songs"
                }

                override fun onArtistAlbumSortChanged() {
                    reported += "artist albums"
                }

                override fun onGenreSortChanged() {
                    reported += "genres"
                }

                override fun onGenreSongSortChanged() {
                    reported += "genre songs"
                }

                override fun onPlaylistSortChanged() {
                    reported += "playlists"
                }
            }
        settings.registerListener(listener)
        for (e in entries) {
            e.set(settings, custom)
        }
        settings.unregisterListener(listener)
        e0().set(settings, byName)
        assertEquals(entries.map { it.name }, reported)
    }

    private fun e0() = entries.first()
}
