/*
 * Copyright (c) 2026 Auxio Project
 * StatsRepositoryExportTest.kt is part of Auxio.
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

import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Exporting what the repository holds, and reading it back in. */
class StatsRepositoryExportTest {
    private class Dao : PlayEventDao {
        val events = mutableListOf<PlayEvent>()

        override suspend fun insert(event: PlayEvent): Long {
            val saved = event.copy(id = events.size + 1L)
            events += saved
            return saved.id
        }

        override suspend fun updateListened(id: Long, listenedMs: Long) {
            val index = events.indexOfFirst { it.id == id }
            events[index] = events[index].copy(listenedMs = listenedMs)
        }

        override suspend fun insertAll(events: List<PlayEvent>) {
            this.events += events
        }

        override suspend fun deleteBaseline() {
            events.removeAll { it.startedAt == PlayEvent.BASELINE_TIME }
        }

        override suspend fun between(fromMs: Long, toMs: Long) = events.filter {
            it.startedAt in fromMs until toMs
        }

        override suspend fun all() = events.sortedBy { it.startedAt }

        override suspend fun deleteAll() = events.clear()
    }

    private fun play(title: String, startedAt: Long) =
        PlayEvent(
            songUid = title,
            title = title,
            artists = "Artist",
            album = "Album",
            startedAt = startedAt,
            listenedMs = 60_000,
            durationMs = 200_000,
        )

    private val repository = StatsRepositoryImpl(Dao())

    @Test
    fun aCloserLookAtAnItemGoesThroughTheWholeHistory() = runBlocking {
        repository.record(play("A", PlayEvent.BASELINE_TIME))
        repository.record(play("A", 1_700_000_000_000))
        repository.record(play("B", 1_700_000_100_000))
        val range = 1_700_000_000_000..1_700_000_050_000
        val stats =
            repository.itemStats(StatsCategory.ARTISTS, "Artist", StatsPeriod.WEEK, range, "?")!!
        assertEquals(1, stats.plays)
        assertEquals(3, stats.allTimePlays)
        assertEquals(
            null,
            repository.itemStats(StatsCategory.ARTISTS, "Nobody", StatsPeriod.WEEK, range, "?"),
        )
    }

    @Test
    fun aBackupCanBeReadBackIntoAnEmptyRepository() = runBlocking {
        repository.record(play("A", 1_700_000_000_000))
        repository.record(play("B", 1_700_000_100_000))
        repository.record(play("Old", PlayEvent.BASELINE_TIME))

        val out = ByteArrayOutputStream()
        repository.exportCsv(out)
        val parsed = StatsCsvImport.parse(out.toString(Charsets.UTF_8))!!

        val fresh = StatsRepositoryImpl(Dao())
        assertEquals(3, fresh.importCsv(listOf(parsed)))
        assertEquals(3, fresh.summary(0L until Long.MAX_VALUE).plays)
    }
}
