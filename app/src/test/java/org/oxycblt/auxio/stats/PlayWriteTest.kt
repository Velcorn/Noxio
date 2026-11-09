/*
 * Copyright (c) 2026 Auxio Project
 * PlayWriteTest.kt is part of Auxio.
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

import java.io.OutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayWriteTest {
    private class FakeRepository : StatsRepository {
        val rows = mutableListOf<PlayEvent>()

        override suspend fun record(event: PlayEvent): Long {
            rows += event.copy(id = rows.size + 1L)
            return rows.last().id
        }

        override suspend fun updateListened(id: Long, listenedMs: Long) {
            val index = rows.indexOfFirst { it.id == id }
            rows[index] = rows[index].copy(listenedMs = listenedMs)
        }

        override suspend fun summary(range: LongRange, unknownArtist: String) = error("not used")

        override suspend fun exportCsv(out: OutputStream) = error("not used")

        override suspend fun importCsv(files: List<StatsCsvImport.Parsed>) = error("not used")
    }

    private val repository = FakeRepository()

    private fun play() =
        PlayEvent(
            songUid = "s",
            title = "Song",
            artists = "A",
            album = "B",
            startedAt = 1_000,
            listenedMs = 0,
            durationMs = 200_000,
        )

    @Test
    fun theFirstWriteSavesThePlay() = runBlocking {
        PlayWrite(repository, ::play).write(30_000)
        assertEquals(1, repository.rows.size)
        assertEquals(30_000L, repository.rows.single().listenedMs)
        assertEquals("Song", repository.rows.single().title)
    }

    @Test
    fun laterWritesUpdateTheSameRowInsteadOfAddingAnother() = runBlocking {
        val write = PlayWrite(repository, ::play)
        write.write(30_000)
        write.write(45_000)
        write.write(60_000)
        assertEquals(1, repository.rows.size)
        assertEquals(60_000L, repository.rows.single().listenedMs)
    }

    @Test
    fun eachPlayHasItsOwnRow() = runBlocking {
        PlayWrite(repository, ::play).write(30_000)
        PlayWrite(repository, ::play).write(40_000) // the same song played again
        assertEquals(listOf(30_000L, 40_000L), repository.rows.map { it.listenedMs })
    }

    @Test
    fun theSongIsOnlyDescribedWhenItIsFirstWritten() = runBlocking {
        var calls = 0
        val write =
            PlayWrite(repository) {
                calls++
                play()
            }
        assertEquals(0, calls)
        write.write(30_000)
        write.write(31_000)
        assertEquals(1, calls)
    }
}
