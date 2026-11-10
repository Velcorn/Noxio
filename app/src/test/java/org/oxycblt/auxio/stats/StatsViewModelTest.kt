/*
 * Copyright (c) 2026 Auxio Project
 * StatsViewModelTest.kt is part of Auxio.
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
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The moves between periods and categories of the stats screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsViewModelTest {
    private class FakeRepository : StatsRepository {
        var imported = 0
        var summaries = mutableListOf<LongRange>()
        var item: ItemStats? = null
        var itemKey: String? = null

        override suspend fun itemStats(
            category: StatsCategory,
            key: String,
            period: StatsPeriod,
            range: LongRange,
            unknownArtist: String,
        ): ItemStats? {
            itemKey = key
            return item
        }

        override suspend fun record(event: PlayEvent) = 1L

        override suspend fun updateListened(id: Long, listenedMs: Long) {}

        override suspend fun summary(range: LongRange, unknownArtist: String): ListeningStats {
            summaries += range
            return summarize(emptyList())
        }

        override suspend fun exportCsv(out: OutputStream) {}

        override suspend fun importCsv(files: List<StatsCsvImport.Parsed>): Int {
            imported += 1
            return 3
        }

        var undated = 2
        var removed = 0

        override suspend fun undatedPlays() = undated

        override suspend fun removeUndated() {
            removed += 1
            undated = 0
        }
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = FakeRepository()
    private val model = StatsViewModel(repository, context)

    @Test
    fun itStartsOnThisWeekWithTheArtists() {
        assertEquals(StatsViewModel.Query(StatsPeriod.WEEK, 0), model.query.value)
        assertEquals(StatsCategory.ARTISTS, model.category.value)
        assertFalse(model.expanded.value)
    }

    @Test
    fun previousGoesBackAndNextForwardButNotPastToday() {
        model.previous()
        model.previous()
        assertEquals(-2, model.query.value.offset)
        model.next()
        assertEquals(-1, model.query.value.offset)
        model.next()
        model.next()
        assertEquals(0, model.query.value.offset)
    }

    @Test
    fun anotherPeriodStartsAtTheCurrentOneAndFoldsTheCard() {
        model.previous()
        model.toggleExpanded()
        model.setPeriod(StatsPeriod.MONTH)
        assertEquals(StatsViewModel.Query(StatsPeriod.MONTH, 0), model.query.value)
        assertFalse(model.expanded.value)
    }

    @Test
    fun theSamePeriodAgainChangesNothing() {
        model.previous()
        model.toggleExpanded()
        model.setPeriod(StatsPeriod.WEEK)
        assertEquals(-1, model.query.value.offset)
        assertTrue(model.expanded.value)
    }

    @Test
    fun anotherCategoryFoldsTheCardButTheSameOneDoesNot() {
        model.toggleExpanded()
        model.setCategory(StatsCategory.ARTISTS)
        assertTrue(model.expanded.value)
        model.setCategory(StatsCategory.SONGS)
        assertEquals(StatsCategory.SONGS, model.category.value)
        assertFalse(model.expanded.value)
    }

    @Test
    fun theRowsShownAreThoseOfTheCategory() {
        fun item(name: String) = RankedItem(name, null, 1, 1L)
        val stats =
            ListeningStats(
                plays = 3,
                listenedMs = 3L,
                songs = listOf(item("song")),
                artists = listOf(item("artist")),
                albums = listOf(item("album")),
            )
        val ui =
            StatsViewModel.Ui(
                model.query.value,
                statsRange(StatsPeriod.WEEK, 0),
                stats,
            )
        assertEquals("artist", model.items(ui).single().name)
        model.setCategory(StatsCategory.ALBUMS)
        assertEquals("album", model.items(ui).single().name)
        model.setCategory(StatsCategory.SONGS)
        assertEquals("song", model.items(ui).single().name)
    }

    @Test
    fun anImportIsPassedOnAndTheCountReturned() = runBlocking {
        assertEquals(3, model.importCsv(emptyList()))
        assertEquals(1, repository.imported)
    }

    /** Let the main thread and the background run until [done], or fail after a few seconds. */
    private fun waitFor(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    @Test
    fun theSummaryOfThePeriodIsLoadedAgainWhenThePeriodChangesOrAnImportHappens() {
        val collector = CoroutineScope(Dispatchers.Unconfined).launch { model.ui.collect {} }
        waitFor { model.ui.value != null }
        assertEquals(StatsPeriod.WEEK, model.ui.value!!.query.period)
        assertEquals(1, repository.summaries.size)

        model.setPeriod(StatsPeriod.YEAR)
        waitFor { model.ui.value?.query?.period == StatsPeriod.YEAR }
        assertEquals(2, repository.summaries.size)

        runBlocking { model.importCsv(emptyList()) }
        waitFor { repository.summaries.size == 3 }
        collector.cancel()
    }

    @Test
    fun aCloserLookIsKeptWithItsRankAndPeriod() {
        val row = RankedItem("Song", "Artist", 2, 2L, key = "the key")
        // Nothing is shown yet, so there is no period to look at.
        assertFalse(runBlocking { model.loadDetails(1, row) })

        val collector = CoroutineScope(Dispatchers.Unconfined).launch { model.ui.collect {} }
        waitFor { model.ui.value != null }
        // Not played at all.
        assertFalse(runBlocking { model.loadDetails(1, row) })
        assertEquals(null, model.details)

        val stats =
            ItemStats(
                category = StatsCategory.ARTISTS,
                name = "Song",
                subtitle = "Artist",
                plays = 2,
                listenedMs = 2L,
                share = 0.5f,
                daysPlayed = 1,
                bucketUnit = ItemStats.BucketUnit.DAY,
                buckets = emptyList(),
                topSongs = emptyList(),
                allTimePlays = 2,
                firstPlayedMs = null,
                lastPlayedMs = null,
            )
        repository.item = stats
        assertTrue(runBlocking { model.loadDetails(3, row) })
        assertEquals("the key", repository.itemKey)
        assertEquals(3, model.details!!.rank)
        assertEquals(stats, model.details!!.stats)
        assertEquals(model.ui.value!!.range, model.details!!.range)
        collector.cancel()
    }

    @Test
    fun exportingIsPassedOnToTheRepository() = runBlocking {
        model.exportCsv(java.io.ByteArrayOutputStream())
    }

    @Test
    fun theUndatedPlaysAreOnlyRemovedWhenAsked() = runBlocking {
        assertEquals(2, model.undatedPlays())
        model.importCsv(emptyList())
        assertEquals(0, repository.removed)
        model.importCsv(emptyList(), removeUndated = true)
        assertEquals(1, repository.removed)
        assertEquals(0, model.undatedPlays())
    }
}
