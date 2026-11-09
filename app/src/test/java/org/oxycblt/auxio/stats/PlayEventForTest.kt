/*
 * Copyright (c) 2026 Auxio Project
 * PlayEventForTest.kt is part of Auxio.
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
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.fs.Components
import org.oxycblt.musikr.fs.Path
import org.oxycblt.musikr.fs.Volume
import org.oxycblt.musikr.tag.Date
import org.oxycblt.musikr.tag.Name
import org.oxycblt.musikr.tag.Placeholder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** How a song becomes the play that is saved for it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class PlayEventForTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun named(raw: String): Name.Known =
        object : Name.Known() {
            override val raw = raw
            override val sort: String? = null
            override val tokens = emptyList<org.oxycblt.musikr.tag.Token>()

            override fun hashCode() = raw.hashCode()

            override fun equals(other: Any?) = other is Name.Known && other.raw == raw
        }

    private inline fun <reified T> fake(vararg values: Pair<String, Any?>): T {
        val answers = values.toMap()
        return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) {
            proxy,
            method,
            args ->
            when {
                method.name in answers -> answers[method.name]
                method.name == "hashCode" -> System.identityHashCode(proxy)
                method.name == "equals" -> proxy === args?.firstOrNull()
                else -> throw UnsupportedOperationException(method.name)
            }
        } as T
    }

    private fun artist(name: Name) = fake<Artist>("getName" to name)

    private fun song(
        artists: List<Artist>,
        albumArtists: List<Artist> = artists,
        genres: List<Genre> = emptyList(),
        date: Date? = null,
    ): Song {
        val album = fake<Album>("getName" to named("The Album"), "getArtists" to albumArtists)
        return fake<Song>(
            "getUid" to Music.UID.fromString("uas00000000-0000-0000-0000-00000000000a"),
            "getName" to named("The Song"),
            "getArtists" to artists,
            "getAlbum" to album,
            "getDurationMs" to 215_000L,
            "getPath" to
                Path(Volume.ThirdParty(Uri.EMPTY), Components.parseUnix("Music/Band/song.mp3")),
            "getGenres" to genres,
            "getDate" to date,
        )
    }

    private val separator = PlayEvent.ARTIST_SEPARATOR

    @Test
    fun aSongIsDescribedWithTheNamesAsTheyAreShown() {
        val genre = fake<Genre>("getName" to named("Dream Pop"))
        val event =
            playEventFor(
                context,
                song(
                    artists = listOf(artist(named("Luna Vale")), artist(named("Kestrel"))),
                    albumArtists = listOf(artist(named("Luna Vale"))),
                    genres = listOf(genre, fake<Genre>("getName" to named("Folk"))),
                    date = Date.from(2023),
                ),
                startedAt = 1_234,
            )
        assertEquals("uas00000000-0000-0000-0000-00000000000a", event.songUid)
        assertEquals("The Song", event.title)
        assertEquals("Luna Vale${separator}Kestrel", event.artists)
        assertEquals("The Album", event.album)
        assertEquals("Luna Vale", event.albumArtist)
        assertEquals("Dream Pop", event.genre)
        assertEquals(2023, event.year)
        assertEquals(215_000L, event.durationMs)
        assertEquals(1_234L, event.startedAt)
        assertEquals(0L, event.listenedMs)
        assertEquals("Music/Band/song.mp3", event.filePath)
    }

    @Test
    fun aSongWithoutAGenreOrADateHasNoneAndAnUnknownArtistIsNamedAsSuch() {
        val event =
            playEventFor(
                context,
                song(artists = listOf(artist(Name.Unknown(Placeholder.ARTIST)))),
                1,
            )
        assertEquals("", event.genre)
        assertEquals(0, event.year)
        assertEquals("Unknown artist", event.artists)
    }
}
