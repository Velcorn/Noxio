/*
 * Copyright (c) 2026 Auxio Project
 * StatsItemTest.kt is part of Auxio.
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

import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The closer look at one artist, album or song of the stats. */
class StatsItemTest {
    private val sep = PlayEvent.ARTIST_SEPARATOR

    private fun calendar() = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT)

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12) =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    /** Tuesday the 10th of November 2026. */
    private val now = at(2026, 11, 10)

    private fun range(period: StatsPeriod, offset: Int = 0) =
        statsRange(period, offset, now, calendar())

    private fun play(
        title: String,
        artists: String = "Artist",
        album: String = "Album",
        startedAt: Long = now,
        listenedMs: Long = 60_000,
    ) =
        PlayEvent(
            songUid = "uid-$title",
            title = title,
            artists = artists,
            album = album,
            startedAt = startedAt,
            listenedMs = listenedMs,
            durationMs = 200_000,
        )

    private fun look(
        events: List<PlayEvent>,
        category: StatsCategory,
        key: String,
        period: StatsPeriod = StatsPeriod.WEEK,
        offset: Int = 0,
    ) =
        summarizeItem(
            events,
            category,
            key,
            period,
            range(period, offset),
            unknownArtist = "Unknown artist",
            calendar = calendar(),
            nowMs = now,
        )

    // --- which plays belong to the item ---

    @Test
    fun `an artist gets every play it is part of`() {
        val events =
            listOf(
                play("A", artists = "X"),
                play("B", artists = "X${sep}Y"),
                play("C", artists = "Z"),
            )
        val stats = look(events, StatsCategory.ARTISTS, "X")!!
        assertEquals("X", stats.name)
        assertNull(stats.subtitle)
        assertEquals(2, stats.plays)
        assertEquals(120_000, stats.listenedMs)
        assertEquals(1, look(events, StatsCategory.ARTISTS, "Y")!!.plays)
    }

    @Test
    fun `plays without an artist belong to the unknown artist`() {
        val events = listOf(play("A", artists = ""), play("B", artists = "X"))
        val stats = look(events, StatsCategory.ARTISTS, "Unknown artist")!!
        assertEquals(1, stats.plays)
        assertEquals("A", stats.topSongs.single().name)
    }

    @Test
    fun `two albums of the same name by different artists stay apart`() {
        val events =
            listOf(
                play("A", artists = "X", album = "Greatest Hits"),
                play("B", artists = "X", album = "Greatest Hits"),
                play("C", artists = "Y", album = "Greatest Hits"),
                play("D", artists = "X", album = ""),
            )
        val stats = look(events, StatsCategory.ALBUMS, albumKey(events[0])!!)!!
        assertEquals("Greatest Hits", stats.name)
        assertEquals("X", stats.subtitle)
        assertEquals(2, stats.plays)
        assertNull(albumKey(events[3]))
    }

    @Test
    fun `a song is found by its title, artists and album`() {
        val events =
            listOf(
                play("A", artists = "X${sep}Y"),
                play("A", artists = "X${sep}Y"),
                play("A", artists = "Z"),
            )
        val stats = look(events, StatsCategory.SONGS, songKey(events[0]))!!
        assertEquals("A", stats.name)
        assertEquals("X, Y", stats.subtitle)
        assertEquals(2, stats.plays)
        assertTrue(stats.topSongs.isEmpty())
    }

    @Test
    fun `the keys of the ranked rows lead to the same items`() {
        val events =
            listOf(play("A", artists = "X"), play("A", artists = "X"), play("B", artists = "Y"))
        val summary = summarize(events)
        assertEquals(2, look(events, StatsCategory.SONGS, summary.songs[0].key)!!.plays)
        assertEquals(2, look(events, StatsCategory.ARTISTS, summary.artists[0].key)!!.plays)
        assertEquals(2, look(events, StatsCategory.ALBUMS, summary.albums[0].key)!!.plays)
    }

    @Test
    fun `there is nothing to look at when it was not played in the period`() {
        val events = listOf(play("A", artists = "X", startedAt = at(2026, 10, 1)))
        assertNull(look(events, StatsCategory.ARTISTS, "X"))
        assertNull(look(events, StatsCategory.ARTISTS, "Nobody", StatsPeriod.ALL))
        assertEquals(1, look(events, StatsCategory.ARTISTS, "X", StatsPeriod.YEAR)!!.plays)
    }

    // --- the numbers ---

    @Test
    fun `the share is the part of the listening time of the period`() {
        val events =
            listOf(
                play("A", artists = "X", listenedMs = 30_000),
                play("B", artists = "Y", listenedMs = 90_000),
                // Outside of the week, so it does not count.
                play("C", artists = "Y", listenedMs = 900_000, startedAt = at(2026, 10, 1)),
            )
        assertEquals(0.25f, look(events, StatsCategory.ARTISTS, "X")!!.share, 0.001f)
    }

    @Test
    fun `without any listening time the plays are compared`() {
        val events =
            listOf(
                play("A", artists = "X", listenedMs = 0),
                play("B", artists = "Y", listenedMs = 0),
                play("C", artists = "Y", listenedMs = 0),
            )
        assertEquals(1 / 3f, look(events, StatsCategory.ARTISTS, "X")!!.share, 0.001f)
    }

    @Test
    fun `days are counted once however often it was played on them`() {
        val events =
            listOf(
                play("A", startedAt = at(2026, 11, 9, 8)),
                play("A", startedAt = at(2026, 11, 9, 20)),
                play("A", startedAt = at(2026, 11, 10, 8)),
            )
        assertEquals(2, look(events, StatsCategory.ARTISTS, "Artist")!!.daysPlayed)
    }

    @Test
    fun `all time counts the plays from before Noxio but they have no day or date`() {
        val events =
            listOf(
                play("A", startedAt = PlayEvent.BASELINE_TIME),
                play("A", startedAt = PlayEvent.BASELINE_TIME),
                play("A", startedAt = at(2026, 3, 5)),
                play("A", startedAt = at(2026, 11, 9)),
            )
        val week = look(events, StatsCategory.SONGS, songKey(events[0]))!!
        assertEquals(1, week.plays)
        assertEquals(4, week.allTimePlays)
        assertEquals(at(2026, 3, 5), week.firstPlayedMs)
        assertEquals(at(2026, 11, 9), week.lastPlayedMs)

        val all = look(events, StatsCategory.SONGS, songKey(events[0]), StatsPeriod.ALL)!!
        assertEquals(4, all.plays)
        assertEquals(2, all.daysPlayed)

        val undated =
            look(events.take(2), StatsCategory.SONGS, songKey(events[0]), StatsPeriod.ALL)!!
        assertNull(undated.firstPlayedMs)
        assertNull(undated.lastPlayedMs)
        assertEquals(0, undated.daysPlayed)
        assertTrue(undated.buckets.isEmpty())
    }

    @Test
    fun `plays with a guessed time count for the totals but not for days, bars or dates`() {
        val events =
            listOf(
                play("A", startedAt = at(2026, 11, 9)).copy(estimated = true),
                play("A", startedAt = at(2026, 11, 10)).copy(estimated = true),
                play("A", startedAt = at(2026, 11, 11)),
            )
        val week = look(events, StatsCategory.SONGS, songKey(events[0]))!!
        assertEquals(3, week.plays)
        assertEquals(2, week.estimatedPlays)
        assertEquals(1, week.daysPlayed)
        assertEquals(1, week.buckets.sumOf { it.plays })
        assertEquals(at(2026, 11, 11), week.firstPlayedMs)
        assertEquals(3, week.allTimePlays)
    }

    @Test
    fun `a week of imported plays has no bars`() {
        val events = listOf(play("A", startedAt = at(2026, 11, 9)).copy(estimated = true))
        val week = look(events, StatsCategory.SONGS, songKey(events[0]))!!
        assertEquals(1, week.plays)
        assertEquals(0, week.daysPlayed)
        assertTrue(week.buckets.isEmpty())
        assertNull(week.firstPlayedMs)
    }

    @Test
    fun `an artist lists its three most played songs of the period`() {
        val events =
            List(4) { play("Four") } +
                List(3) { play("Three") } +
                List(2) { play("Two") } +
                play("One") +
                List(9) { play("Earlier", startedAt = at(2026, 10, 1)) }
        val stats = look(events, StatsCategory.ARTISTS, "Artist")!!
        assertEquals(listOf("Four", "Three", "Two"), stats.topSongs.map { it.name })
        assertEquals(listOf(4, 3, 2), stats.topSongs.map { it.plays })
    }

    // --- the chart ---

    @Test
    fun `a week has a bar for each day`() {
        val events =
            listOf(
                play("A", startedAt = at(2026, 11, 9, 0)),
                play("A", startedAt = at(2026, 11, 10, 8)),
                play("A", startedAt = at(2026, 11, 10, 23)),
            )
        val stats = look(events, StatsCategory.ARTISTS, "Artist")!!
        assertEquals(ItemStats.BucketUnit.DAY, stats.bucketUnit)
        assertEquals(listOf(1, 2, 0, 0, 0, 0, 0), stats.buckets.map { it.plays })
        assertEquals(at(2026, 11, 9, 0), stats.buckets.first().startMs)
    }

    @Test
    fun `a month has a bar for each of its days`() {
        val events = listOf(play("A", startedAt = at(2026, 11, 30, 23)))
        val stats = look(events, StatsCategory.ARTISTS, "Artist", StatsPeriod.MONTH)!!
        assertEquals(30, stats.buckets.size)
        assertEquals(1, stats.buckets.last().plays)
        assertEquals(1, stats.buckets.sumOf { it.plays })
    }

    @Test
    fun `a year has a bar for each month`() {
        val events =
            listOf(play("A", startedAt = at(2026, 1, 1, 0)), play("A", startedAt = at(2026, 11, 9)))
        val stats = look(events, StatsCategory.ARTISTS, "Artist", StatsPeriod.YEAR)!!
        assertEquals(ItemStats.BucketUnit.MONTH, stats.bucketUnit)
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0), stats.buckets.map { it.plays })
    }

    @Test
    fun `all time goes by month from the first play until now while that is short`() {
        val events =
            listOf(
                play("A", startedAt = PlayEvent.BASELINE_TIME),
                play("A", startedAt = at(2026, 3, 20)),
                play("A", startedAt = at(2026, 11, 9)),
            )
        val stats = look(events, StatsCategory.ARTISTS, "Artist", StatsPeriod.ALL)!!
        assertEquals(ItemStats.BucketUnit.MONTH, stats.bucketUnit)
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 0, 0, 1), stats.buckets.map { it.plays })
        assertEquals(at(2026, 3, 1, 0), stats.buckets.first().startMs)
    }

    @Test
    fun `all time goes by year once there are more than two years`() {
        val events =
            listOf(
                play("A", startedAt = at(2023, 6, 20)),
                play("A", startedAt = at(2025, 1, 1, 0)),
                play("A", startedAt = at(2026, 11, 9)),
            )
        val stats = look(events, StatsCategory.ARTISTS, "Artist", StatsPeriod.ALL)!!
        assertEquals(ItemStats.BucketUnit.YEAR, stats.bucketUnit)
        assertEquals(listOf(1, 0, 1, 1), stats.buckets.map { it.plays })
        assertEquals(at(2023, 1, 1, 0), stats.buckets.first().startMs)
    }

    @Test
    fun `exactly two years are still charted by month`() {
        assertEquals(
            ItemStats.BucketUnit.MONTH,
            bucketUnit(StatsPeriod.ALL, at(2024, 12, 1), now, calendar()),
        )
        assertEquals(
            ItemStats.BucketUnit.YEAR,
            bucketUnit(StatsPeriod.ALL, at(2024, 11, 30), now, calendar()),
        )
        assertEquals(ItemStats.BucketUnit.MONTH, bucketUnit(StatsPeriod.ALL, null, now, calendar()))
    }

    @Test
    fun `a play before the first bar is left out and one after the last goes into it`() {
        val counted = buckets(listOf(5, 10, 15, 20, 99), listOf(10, 20))
        assertEquals(listOf(2, 2), counted.map { it.plays })
        assertTrue(buckets(listOf(1, 2), emptyList()).isEmpty())
    }

    // --- the file name ---

    @Test
    fun `the image is named after the item`() {
        assertEquals("noxio-lil-peep.png", statsImageFileName("Lil Peep"))
        assertEquals("noxio-ac-dc.png", statsImageFileName("AC/DC"))
        assertEquals("noxio-björk.png", statsImageFileName("Björk"))
        assertEquals("noxio-stats.png", statsImageFileName("???"))
        assertEquals(
            "noxio-".length + 40 + ".png".length,
            statsImageFileName("a".repeat(90)).length,
        )
    }
}
