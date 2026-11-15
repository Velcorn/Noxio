/*
 * Copyright (c) 2026 Auxio Project
 * TagEditTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.tags

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.fs.Components
import org.oxycblt.musikr.fs.Path
import org.oxycblt.musikr.metadata.TagWriter
import org.oxycblt.musikr.tag.Date
import org.oxycblt.musikr.tag.Disc
import org.oxycblt.musikr.tag.Name
import org.oxycblt.musikr.tag.Placeholder
import org.oxycblt.musikr.tag.ReplayGainAdjustment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TagEditTest {
    private fun known(text: String) = mockk<Name.Known> { every { raw } returns text }

    private fun artist(text: String) = mockk<Artist> { every { name } returns known(text) }

    private val album =
        mockk<Album> {
            every { name } returns known("Album")
            every { artists } returns listOf(artist("A"))
        }

    private val song =
        mockk<Song> {
            every { name } returns known("Song")
            every { artists } returns listOf(artist("A"), artist("B"))
            every { album } returns this@TagEditTest.album
            every { date } returns Date.from(2020)
            every { track } returns 3
            every { disc } returns Disc(1, null)
            every { replayGainAdjustment } returns ReplayGainAdjustment(-6.523f, null)
        }

    private val before = TagEdit.of(song)

    private fun edit(vararg changes: Pair<TagField, String>) =
        TagEdit.changes(before, before + changes)

    @Test
    fun aSongIsShownAsText() {
        assertEquals("Song", before[TagField.TITLE])
        assertEquals("A; B", before[TagField.ARTIST])
        assertEquals("A", before[TagField.ALBUM_ARTIST])
        assertEquals("2020", before[TagField.DATE])
        assertEquals("3", before[TagField.TRACK])
        assertEquals("1", before[TagField.DISC])
    }

    @Test
    fun anUnknownNameIsEmpty() {
        val unknown = mockk<Artist> { every { name } returns Name.Unknown(Placeholder.ARTIST) }
        every { album.artists } returns listOf(unknown)
        assertEquals("", TagEdit.of(song)[TagField.ALBUM_ARTIST])
    }

    @Test
    fun nothingChangedWritesNothing() {
        assertEquals(TagEditResult.Write(emptyMap()), TagEdit.changes(before, before))
        // Spaces around do not count as a change.
        assertEquals(TagEditResult.Write(emptyMap()), edit(TagField.TITLE to "  Song "))
    }

    @Test
    fun onlyTheChangedFieldsAreWritten() {
        assertEquals(
            TagEditResult.Write(
                mapOf(TagWriter.TITLE to listOf("New"), TagWriter.TRACK to listOf("12"))
            ),
            edit(TagField.TITLE to "New", TagField.TRACK to "12"),
        )
    }

    @Test
    fun severalArtistsAreSplit() {
        assertEquals(
            TagEditResult.Write(mapOf(TagWriter.ARTIST to listOf("A", "C", "D"))),
            edit(TagField.ARTIST to "A; C ;D;"),
        )
    }

    @Test
    fun anEmptiedFieldIsRemoved() {
        assertEquals(
            TagEditResult.Write(mapOf(TagWriter.DATE to emptyList())),
            edit(TagField.DATE to " "),
        )
    }

    @Test
    fun invalidFieldsAreNamed() {
        assertEquals(
            TagEditResult.Invalid(setOf(TagField.TRACK, TagField.DISC, TagField.DATE)),
            edit(
                TagField.TRACK to "three",
                TagField.DISC to "0",
                TagField.DATE to "yesterday",
                TagField.TITLE to "Fine",
            ),
        )
    }

    @Test
    fun measuredValuesAreWrittenWhereTheyAreUsed() {
        // No tags: both values with their peaks.
        assertEquals(
            mapOf(
                TagWriter.TRACK_GAIN to listOf("-5.00 dB"),
                TagWriter.TRACK_PEAK to listOf("0.900000"),
                TagWriter.ALBUM_GAIN to listOf("-6.00 dB"),
                TagWriter.ALBUM_PEAK to listOf("0.950000"),
            ),
            ReplayGainTags.fields(null, null, -5f, -6f, 0.9f, 0.95f, preferTags = true),
        )
        // A tag that is preferred stays.
        assertEquals(
            mapOf(TagWriter.ALBUM_GAIN to listOf("-6.00 dB")),
            ReplayGainTags.fields(-3f, null, -5f, -6f, null, null, preferTags = true),
        )
        // A tag that is not preferred is replaced, unless it is the same.
        assertEquals(
            mapOf(TagWriter.TRACK_GAIN to listOf("-5.00 dB")),
            ReplayGainTags.fields(-3f, -6f, -5f, -6.001f, null, null, preferTags = false),
        )
        // 0 dB is no adjustment, and reads back as no tag.
        assertEquals(
            mapOf(TagWriter.ALBUM_GAIN to listOf("-6.00 dB")),
            ReplayGainTags.fields(null, null, -0.004f, -6f, null, null, preferTags = true),
        )
        // Nothing measured, nothing written.
        assertEquals(
            emptyMap<String, List<String>>(),
            ReplayGainTags.fields(null, null, null, null, 1f, 1f, preferTags = false),
        )
    }

    @Test
    fun anEditedSongIsFollowedToItsNewUid() {
        val old = Music.UID.fromString("uas00000000-0000-0000-0000-00000000000a")!!
        val new = Music.UID.fromString("uas00000000-0000-0000-0000-00000000000b")!!
        val volume = mockk<org.oxycblt.musikr.fs.Volume.Internal>()
        val renamedPath = Path(volume, Components.parseUnix("a.mp3"))
        val samePath = Path(volume, Components.parseUnix("b.mp3"))
        val waitingPath = Path(volume, Components.parseUnix("c.mp3"))
        val gonePath = Path(volume, Components.parseUnix("d.mp3"))
        val edited =
            mapOf(
                renamedPath to Edited(old, 1),
                samePath to Edited(old, 1),
                waitingPath to Edited(old, 1),
                gonePath to Edited(old, 1),
            )
        val (done, renamed) =
            renames(edited) {
                when (it) {
                    renamedPath -> new to 2L
                    samePath -> old to 2L
                    // Not written yet: the same time as before.
                    waitingPath -> new to 1L
                    else -> null
                }
            }
        assertEquals(setOf(renamedPath, samePath), done.toSet())
        assertEquals(mapOf(old to new), renamed)
    }
}
