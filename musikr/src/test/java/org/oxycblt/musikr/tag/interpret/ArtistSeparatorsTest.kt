/*
 * Copyright (c) 2026 Auxio Project
 * ArtistSeparatorsTest.kt is part of Auxio.
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
 
package org.oxycblt.musikr.tag.interpret

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.musikr.Interpretation
import org.oxycblt.musikr.fs.File
import org.oxycblt.musikr.fs.Path
import org.oxycblt.musikr.metadata.Properties
import org.oxycblt.musikr.pipeline.RawSong
import org.oxycblt.musikr.tag.parse.ParsedTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtistSeparatorsTest {
    private val file =
        mockk<File> {
            every { uri } returns Uri.parse("content://music/song.mp3")
            every { path } returns
                mockk<Path> {
                    every { name } returns "song.mp3"
                    every { directory } returns mockk<Path> { every { name } returns "Music" }
                }
            every { size } returns 100L
            every { mimeType } returns "audio/mpeg"
            every { modifiedMs } returns 0L
        }

    @Test
    fun onlyArtistNamesAreSplitAndNativeValuesStaySeparate() {
        val interpretation =
            Interpretation(Naming.simple(), Separators.from(""), Separators.from(";"))
        val tags =
            ParsedTags(
                durationMs = 1000,
                name = "Song",
                albumName = "Album",
                artistNames = listOf("Lil Peep; Lil Tray"),
                albumArtistNames = listOf("Lil Peep; Lil Tray"),
                genreNames = listOf("Rhythm; Blues"),
                releaseTypes = listOf("Album; EP"),
            )
        val song =
            TagInterpreter.new(interpretation)
                .interpret(RawSong(file, Properties("audio/mpeg", 1000, 128, 44100), tags, null, 0))

        assertEquals(listOf("Lil Peep", "Lil Tray"), song.preArtists.map { it.rawName })
        assertEquals(
            listOf("Lil Peep", "Lil Tray"),
            song.preAlbum.preArtists.preArtists.map { it.rawName },
        )
        assertEquals(listOf("Rhythm; Blues"), song.preGenres.map { it.rawName })

        val nativeTags = tags.copy(artistNames = listOf("A", "B"))
        val nativeSong =
            TagInterpreter.new(interpretation)
                .interpret(
                    RawSong(file, Properties("audio/mpeg", 1000, 128, 44100), nativeTags, null, 0)
                )
        assertEquals(listOf("A", "B"), nativeSong.preArtists.map { it.rawName })
    }
}
