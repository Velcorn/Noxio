/*
 * Copyright (c) 2026 Auxio Project
 * ArtistPicturesTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.image

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistPicturesTest {
    @Test
    fun choicesRoundTripAndCanBeCleared() {
        val choices = mapOf("artist" to "album")
        assertEquals(choices, ArtistPictures.decode(ArtistPictures.encode(choices)))
        assertEquals(emptyMap<String, String>(), ArtistPictures.choose(choices, "artist", null))
    }

    @Test
    fun unresolvedOrForeignAlbumFallsBackToCollage() {
        val choices = mapOf("artist" to "old-album")
        assertEquals(null, ArtistPictures.resolve(choices, "artist", listOf("current-album")))
        assertEquals(null, ArtistPictures.resolve(choices, "other-artist", listOf("old-album")))
        assertEquals("old-album", ArtistPictures.resolve(choices, "artist", listOf("old-album")))
    }

    @Test
    fun severalChoicesAreKeptAndBrokenLinesIgnored() {
        val choices = mapOf("a" to "x", "b" to "y")
        assertEquals(choices, ArtistPictures.decode(ArtistPictures.encode(choices)))
        assertEquals(mapOf("a" to "x"), ArtistPictures.decode("a\tx\nbroken\n\tz\nc\t"))
        assertEquals(emptyMap<String, String>(), ArtistPictures.decode(null))
        assertEquals(mapOf("a" to "z", "b" to "y"), ArtistPictures.choose(choices, "a", "z"))
    }
}
