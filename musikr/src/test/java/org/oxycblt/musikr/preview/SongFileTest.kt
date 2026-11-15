/*
 * Copyright (c) 2026 Auxio Project
 * SongFileTest.kt is part of Auxio.
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
 
package org.oxycblt.musikr.preview

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.SongFile
import org.oxycblt.musikr.fs.Components
import org.oxycblt.musikr.fs.Format
import org.oxycblt.musikr.fs.Path
import org.oxycblt.musikr.fs.Volume
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SongFileTest {
    private val internal =
        mockk<Volume.Internal> { every { mediaStoreName } returns "external_primary" }
    private val sdCard = mockk<Volume.External> { every { mediaStoreName } returns "1234-abcd" }

    private fun song(volume: Volume) =
        mockk<Song> {
            every { uri } returns Uri.parse("content://media/external/audio/media/7")
            every { path } returns Path(volume, Components.parseUnix("Music/Album/01 Song.flac"))
            every { format } returns Format.FLAC
            every { size } returns 12_345
            every { modifiedMs } returns 1_700_000_000_000
        }

    @Test
    fun aSongOnAVolumeIsFoundOnItAgain() {
        val file = SongFile.of(song(sdCard))!!
        assertEquals("1234-abcd", file.volume)
        assertEquals("Music/Album/01 Song.flac", file.path)
        assertEquals("audio/flac", file.mimeType)
        assertEquals(12_345L, file.size)
        assertEquals(1_700_000_000_000L, file.modifiedMs)
        assertSame(sdCard, SongFile.volumeOf(file.volume, listOf(internal, sdCard)))
    }

    @Test
    fun aThirdPartyLocationKeepsItsUri() {
        val tree = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMusic")
        val file = SongFile.of(song(Volume.ThirdParty(tree)))!!
        assertEquals(Volume.ThirdParty(tree), SongFile.volumeOf(file.volume, emptyList()))
    }

    @Test
    fun aVolumeThatIsGoneIsNotFound() {
        val file = SongFile.of(song(sdCard))!!
        assertNull(SongFile.volumeOf(file.volume, listOf(internal)))
    }

    @Test
    fun aVolumeWithoutANameCannotBeSaved() {
        val unnamed = mockk<Volume.Internal> { every { mediaStoreName } returns null }
        assertNull(SongFile.of(song(unnamed)))
    }
}
