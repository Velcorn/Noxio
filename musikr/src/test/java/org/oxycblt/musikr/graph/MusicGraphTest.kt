/*
 * Copyright (c) 2026 Auxio Project
 * MusicGraphTest.kt is part of Auxio.
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
 
package org.oxycblt.musikr.graph

import android.net.Uri
import io.mockk.mockk
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.playlist.PlaylistHandle
import org.oxycblt.musikr.playlist.SongPointer
import org.oxycblt.musikr.playlist.interpret.PrePlaylist
import org.oxycblt.musikr.tag.ReleaseType
import org.oxycblt.musikr.tag.ReplayGainAdjustment
import org.oxycblt.musikr.tag.interpret.Naming
import org.oxycblt.musikr.tag.interpret.PreAlbum
import org.oxycblt.musikr.tag.interpret.PreArtist
import org.oxycblt.musikr.tag.interpret.PreArtistsFrom
import org.oxycblt.musikr.tag.interpret.PreGenre
import org.oxycblt.musikr.tag.interpret.PreSong
import org.oxycblt.musikr.util.update
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The graph connects songs, albums, artists, genres and playlists. Its main job is to merge what
 * the tags spell differently but mean the same, and to drop MusicBrainz IDs that disagree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class MusicGraphTest {
    private val naming = Naming.simple()
    private val mbidOne = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val mbidTwo = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private fun artist(name: String, mbid: UUID? = null) =
        PreArtist(mbid, naming.name(name, null), name)

    private fun genre(name: String) = PreGenre(naming.name(name, null), name)

    private fun album(
        name: String,
        artists: List<PreArtist>,
        mbid: UUID? = null,
    ) =
        PreAlbum(
            mbid,
            naming.name(name, null),
            name,
            ReleaseType.Album(null),
            PreArtistsFrom.Album(artists),
        )

    private fun uid(seed: String) = Music.UID.auxio(Music.UID.Item.SONG) { update(seed) }

    private fun song(
        title: String,
        album: PreAlbum,
        artists: List<PreArtist>,
        genres: List<PreGenre> = emptyList(),
        v363: Music.UID = uid(title),
        v400: Music.UID = uid("$title 400"),
        v401: Music.UID = uid("$title 401"),
    ) =
        PreSong(
            v363Uid = v363,
            v400Uid = v400,
            v401Uid = v401,
            musicBrainzId = null,
            name = naming.name(title, null),
            rawName = title,
            track = null,
            disc = null,
            date = null,
            uri = Uri.EMPTY,
            path = mockk(relaxed = true),
            format = mockk(relaxed = true),
            size = 1,
            durationMs = 1_000,
            bitrateKbps = 1,
            sampleRateHz = 1,
            replayGainAdjustment = ReplayGainAdjustment(null, null),
            modifiedMs = 0,
            addedMs = 0,
            cover = null,
            preAlbum = album,
            preArtists = artists,
            preGenres = genres,
        )

    private fun build(
        vararg songs: PreSong,
        playlists: List<PrePlaylist> = emptyList(),
    ): MusicGraph {
        val builder = MusicGraph.builder()
        songs.forEach(builder::add)
        playlists.forEach(builder::add)
        return builder.build()
    }

    @Test
    fun anEmptyGraphIsEmpty() {
        val graph = build()
        assertTrue(graph.songVertex.isEmpty())
        assertTrue(graph.albumVertex.isEmpty())
        assertTrue(graph.artistVertex.isEmpty())
        assertTrue(graph.genreVertex.isEmpty())
        assertTrue(graph.playlistVertex.isEmpty())
    }

    @Test
    fun songsOfOneAlbumAndArtistShareThem() {
        val artist = artist("Slowdive")
        val album = album("Souvlaki", listOf(artist))
        val graph =
            build(song("Alison", album, listOf(artist)), song("Machine Gun", album, listOf(artist)))
        assertEquals(2, graph.songVertex.size)
        assertEquals(1, graph.albumVertex.size)
        assertEquals(1, graph.artistVertex.size)
        val albumVertex = graph.albumVertex.single()
        assertEquals(2, albumVertex.songVertices.size)
        assertSame(albumVertex, graph.songVertex[0].albumVertex)
        val artistVertex = graph.artistVertex.single()
        assertEquals(2, artistVertex.songVertices.size)
        assertEquals(setOf(albumVertex), artistVertex.albumVertices)
        assertEquals(listOf(artistVertex), albumVertex.artistVertices)
    }

    @Test
    fun aSongThatIsAddedTwiceCountsOnce() {
        val artist = artist("Slowdive")
        val album = album("Souvlaki", listOf(artist))
        val same = uid("Alison")
        val graph =
            build(
                song("Alison", album, listOf(artist), v363 = same),
                song("Alison (again)", album, listOf(artist), v363 = same),
            )
        assertEquals(1, graph.songVertex.size)
        assertEquals("Alison", graph.songVertex.single().preSong.rawName)
    }

    @Test
    fun genresAreLinkedToSongsAndArtists() {
        val artist = artist("Slowdive")
        val album = album("Souvlaki", listOf(artist))
        val shoegaze = genre("Shoegaze")
        val graph = build(song("Alison", album, listOf(artist), listOf(shoegaze)))
        val genreVertex = graph.genreVertex.single()
        assertEquals(graph.songVertex.toSet(), genreVertex.songVertices)
        assertEquals(graph.artistVertex.toSet(), genreVertex.artistVertices)
        assertEquals(setOf(genreVertex), graph.artistVertex.single().genreVertices)
    }

    @Test
    fun genresThatOnlyDifferInCaseBecomeTheMostCommonSpelling() {
        val artist = artist("Slowdive")
        val album = album("Souvlaki", listOf(artist))
        val graph =
            build(
                song("One", album, listOf(artist), listOf(genre("Shoegaze"))),
                song("Two", album, listOf(artist), listOf(genre("shoegaze"))),
                song("Three", album, listOf(artist), listOf(genre("shoegaze"))),
            )
        val genreVertex = graph.genreVertex.single()
        assertEquals("shoegaze", genreVertex.preGenre.rawName)
        assertEquals(3, genreVertex.songVertices.size)
        assertTrue(graph.songVertex.all { it.genreVertices == listOf(genreVertex) })
        assertEquals(setOf(genreVertex), graph.artistVertex.single().genreVertices)
    }

    @Test
    fun artistsWhoseNamesOnlyDifferInCaseAreOneArtist() {
        val lower = artist("alt-j")
        val upper = artist("Alt-J")
        val graph =
            build(
                song("One", album("An Awesome Wave", listOf(upper)), listOf(upper)),
                song("Two", album("This Is All Yours", listOf(lower)), listOf(lower)),
                song("Three", album("This Is All Yours", listOf(lower)), listOf(lower)),
            )
        val artistVertex = graph.artistVertex.single()
        assertEquals("alt-j", artistVertex.preArtist.rawName)
        assertEquals(3, artistVertex.songVertices.size)
        assertEquals(2, artistVertex.albumVertices.size)
        assertTrue(graph.songVertex.all { it.artistVertices == listOf(artistVertex) })
    }

    @Test
    fun artistsWithDifferentIdsKeepApartIfAllHaveOne() {
        val first = artist("Phoenix", mbidOne)
        val second = artist("Phoenix", mbidTwo)
        val graph =
            build(
                song("One", album("A", listOf(first)), listOf(first)),
                song("Two", album("B", listOf(second)), listOf(second)),
            )
        assertEquals(2, graph.artistVertex.size)
    }

    @Test
    fun theIdOfAnArtistIsDroppedIfOnlySomeSongsHaveOne() {
        val withId = artist("Phoenix", mbidOne)
        val without = artist("Phoenix")
        val graph =
            build(
                song("One", album("A", listOf(withId)), listOf(withId)),
                song("Two", album("B", listOf(without)), listOf(without)),
            )
        val artistVertex = graph.artistVertex.single()
        assertNull(artistVertex.preArtist.musicBrainzId)
        assertEquals(2, artistVertex.songVertices.size)
        assertEquals(2, artistVertex.albumVertices.size)
    }

    @Test
    fun anArtistListedTwiceOnASongIsLinkedOnce() {
        val a = artist("Phoenix")
        val b = artist("phoenix")
        val album = album("A", listOf(a))
        val graph = build(song("One", album, listOf(a, b)), song("Two", album, listOf(a)))
        val song = graph.songVertex.first { it.preSong.rawName == "One" }
        assertEquals(1, song.artistVertices.size)
    }

    @Test
    fun albumsWithTheSameNameAreOneAlbumAndTheIdIsDroppedIfOnlySomeHaveOne() {
        val artist = artist("Phoenix")
        val withId = album("Wolfgang", listOf(artist), mbidOne)
        val without = album("Wolfgang", listOf(artist))
        val graph = build(song("One", withId, listOf(artist)), song("Two", without, listOf(artist)))
        val albumVertex = graph.albumVertex.single()
        assertNull(albumVertex.preAlbum.musicBrainzId)
        assertEquals(2, albumVertex.songVertices.size)
        assertEquals(setOf(albumVertex), graph.artistVertex.single().albumVertices)
    }

    @Test
    fun albumsOfTheSameNameWithDifferentIdsStayApart() {
        val artist = artist("Phoenix")
        val graph =
            build(
                song("One", album("Wolfgang", listOf(artist), mbidOne), listOf(artist)),
                song("Two", album("Wolfgang", listOf(artist), mbidTwo), listOf(artist)),
            )
        assertEquals(2, graph.albumVertex.size)
    }

    @Test
    fun playlistsFindTheirSongsByAnyOfTheirIds() {
        val artist = artist("Slowdive")
        val album = album("Souvlaki", listOf(artist))
        val one = song("Alison", album, listOf(artist))
        val two = song("Machine Gun", album, listOf(artist))
        val three = song("Dagger", album, listOf(artist))
        val playlist =
            PrePlaylist(
                naming.name("Mine", null),
                "Mine",
                mockk<PlaylistHandle>(relaxed = true),
                listOf(
                    SongPointer.UID(one.v363Uid),
                    SongPointer.UID(two.v400Uid),
                    SongPointer.UID(three.v401Uid),
                    SongPointer.UID(uid("gone")),
                    SongPointer.UID(one.v363Uid),
                ),
            )
        val graph = build(one, two, three, playlists = listOf(playlist))
        val songs = graph.playlistVertex.single().songVertices.map { it?.preSong?.rawName }
        assertEquals(listOf("Alison", "Machine Gun", "Dagger", null, "Alison"), songs)
    }
}
