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
import org.junit.Assert.assertNull
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
            looked += category to period
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

        var review: YearInReview? = null
        val looked = mutableListOf<Pair<StatsCategory, StatsPeriod>>()

        override suspend fun yearInReview(year: Int, unknownArtist: String) =
            review ?: yearInReview(emptyList(), year, unknownArtist)
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
    fun theWrappedYearIsTheShownYearOrTheCurrentOne() {
        val now = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        assertEquals(now, model.wrappedYear())
        val collector = CoroutineScope(Dispatchers.Unconfined).launch { model.ui.collect {} }
        model.setPeriod(StatsPeriod.YEAR)
        model.previous()
        waitFor { model.ui.value?.query == StatsViewModel.Query(StatsPeriod.YEAR, -1) }
        assertEquals(now - 1, model.wrappedYear())
        collector.cancel()
    }

    @Test
    fun theWrappedCardsLookCloserAtTheTopArtistAndAlbumOfTheYear() = runBlocking {
        val events =
            listOf(
                PlayEvent(
                    songUid = "u",
                    title = "Song",
                    artists = "Star",
                    album = "Big",
                    startedAt =
                        java.util.Calendar.getInstance()
                            .apply {
                                clear()
                                set(2026, 4, 1)
                            }
                            .timeInMillis,
                    listenedMs = 1,
                    durationMs = 1,
                )
            )
        repository.review = yearInReview(events, 2026)
        val wrapped = model.loadWrapped(2026)
        assertEquals(2026, wrapped.review.year)
        assertEquals(
            listOf(
                StatsCategory.ARTISTS to StatsPeriod.YEAR,
                StatsCategory.ALBUMS to StatsPeriod.YEAR,
            ),
            repository.looked,
        )
    }

    @Test
    fun anEmptyYearHasNothingToLookCloserAt() = runBlocking {
        val wrapped = model.loadWrapped(2001)
        assertTrue(wrapped.review.isEmpty)
        assertNull(wrapped.topArtist)
        assertTrue(repository.looked.isEmpty())
    }

    @Test
    fun aMusicoletFileNameGivesItsWeek() {
        val parsed =
            model.parseCsv(
                "FILE_PATH,TITLE,ARTIST,ALBUM,ALBUM_ARTIST,COMPOSER,GENRE,YEAR,DURATION_MS,PLAY_COUNT\n" +
                    "a.mp3,A,X,Al,x,x,x,0,1,2\n",
                "Most played Songs • Week 22 - 2026.csv",
            ) as StatsCsvImport.Parsed.MostPlayed
        assertEquals(22, parsed.period?.number)
    }
}
