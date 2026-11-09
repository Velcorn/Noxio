/*
 * Copyright (c) 2026 Auxio Project
 * SearchEngineTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.search

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.fs.Components
import org.oxycblt.musikr.fs.Path
import org.oxycblt.musikr.fs.Volume
import org.oxycblt.musikr.tag.Name
import org.oxycblt.musikr.tag.Placeholder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The matching of the search: names, sort names, accents, file names. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class SearchEngineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val engine = SearchEngineImpl(context)

    private fun known(raw: String, sort: String? = null): Name =
        object : Name.Known() {
            override val raw = raw
            override val sort = sort
            override val tokens = emptyList<org.oxycblt.musikr.tag.Token>()

            override fun hashCode() = raw.hashCode()

            override fun equals(other: Any?) = other is Name.Known && other.raw == raw
        }

    /** A music item that only knows its name (and path), which is all the search looks at. */
    private inline fun <reified T : Music> fake(name: Name, path: Path? = null): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) {
            proxy,
            method,
            args ->
            when (method.name) {
                "getName" -> name
                "getPath" -> path
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "fake ${name}"
                else -> throw UnsupportedOperationException(method.name)
            }
        } as T

    private fun artist(raw: String, sort: String? = null) = fake<Artist>(known(raw, sort))

    private fun album(raw: String) = fake<Album>(known(raw))

    private fun names(result: Collection<Artist>?) = result?.map { (it.name as Name.Known).raw }

    private fun search(query: String, vararg artists: Artist) = runBlocking {
        engine.search(SearchEngine.Items(artists = artists.toList()), query).artists
    }

    @Test
    fun aNameMatchesAnywhereInItAndIgnoringCase() {
        val result = search("night", artist("The Night Owls"), artist("Kestrel"))
        assertEquals(listOf("The Night Owls"), names(result))
    }

    @Test
    fun nothingMatchingGivesNullNotAnEmptyList() {
        assertNull(search("zzz", artist("Kestrel")))
    }

    @Test
    fun theSortNameMatchesToo() {
        val result = search("owls, the", artist("The Night Owls", sort = "Night Owls, The"))
        assertEquals(listOf("The Night Owls"), names(result))
    }

    @Test
    fun accentsAndPunctuationAreIgnored() {
        assertEquals(listOf("Mötley Crüe"), names(search("motley", artist("Mötley Crüe"))))
        assertEquals(listOf("AC/DC"), names(search("acdc", artist("AC/DC"))))
    }

    @Test
    fun anUnknownNameMatchesItsPlaceholder() {
        val unknown = fake<Artist>(Name.Unknown(Placeholder.ARTIST))
        assertEquals(1, search("unknown", unknown)?.size)
    }

    @Test
    fun everyTypeIsSearchedAndOnlyTheOnesGiven() = runBlocking {
        val items =
            SearchEngine.Items(
                albums = listOf(album("Quiet Engines"), album("Winter Songs")),
                artists = listOf(artist("Engine Room")),
            )
        val result = engine.search(items, "engine")
        assertEquals(1, result.albums?.size)
        assertEquals(1, result.artists?.size)
        assertNull(result.songs)
        assertNull(result.genres)
        assertNull(result.playlists)
    }

    @Test
    fun aSongAlsoMatchesItsFileName() = runBlocking {
        val path =
            Path(Volume.ThirdParty(Uri.EMPTY), Components.parseUnix("Music/track07_final.mp3"))
        val song = fake<Song>(known("Seven"), path)
        val other =
            fake<Song>(
                known("Eight"),
                Path(Volume.ThirdParty(Uri.EMPTY), Components.parseUnix("Music/eight.mp3")),
            )
        val result = engine.search(SearchEngine.Items(songs = listOf(song, other)), "final")
        assertEquals(listOf(song), result.songs?.toList())
    }
}
