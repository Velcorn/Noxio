/*
 * Copyright (c) 2026 Auxio Project
 * StatsDatabaseTest.kt is part of Auxio.
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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The queries on a real (in memory) database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsDatabaseTest {
    private val database =
        Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                StatsDatabase::class.java,
            )
            .allowMainThreadQueries()
            .build()
    private val dao = database.playEventDao()

    @After fun close() = database.close()

    private fun play(title: String, startedAt: Long, listenedMs: Long = 1_000) =
        PlayEvent(
            songUid = "uid-$title",
            title = title,
            artists = "Artist",
            album = "Album",
            startedAt = startedAt,
            listenedMs = listenedMs,
            durationMs = 200_000,
        )

    @Test
    fun anInsertedPlayGetsAnIdAndComesBack() = runBlocking {
        val id = dao.insert(play("A", 10))
        assertEquals(listOf(id), dao.all().map { it.id })
        assertEquals("A", dao.all().single().title)
    }

    @Test
    fun theListenedTimeOfAPlayCanBeUpdated() = runBlocking {
        val id = dao.insert(play("A", 10, listenedMs = 1_000))
        dao.updateListened(id, 90_000)
        assertEquals(90_000L, dao.all().single().listenedMs)
    }

    @Test
    fun aRangeHasItsStartButNotItsEndAndTheHistoryIsInOrder() = runBlocking {
        dao.insertAll(listOf(play("late", 30), play("early", 10), play("middle", 20)))
        assertEquals(listOf("early", "middle", "late"), dao.all().map { it.title })
        assertEquals(listOf("middle"), dao.between(20, 30).map { it.title })
        assertEquals(listOf("early", "middle"), dao.between(10, 30).map { it.title })
    }

    @Test
    fun onlyThePlaysFromBeforeNoxioAreRemovedByAnImport() = runBlocking {
        dao.insertAll(listOf(play("old", PlayEvent.BASELINE_TIME), play("new", 50)))
        dao.deleteBaseline()
        assertEquals(listOf("new"), dao.all().map { it.title })
    }

    @Test
    fun everythingCanBeDeleted() = runBlocking {
        dao.insertAll(listOf(play("A", 1), play("B", 2)))
        dao.deleteAll()
        assertEquals(emptyList<PlayEvent>(), dao.all())
    }

    @Test
    fun theLongerTableOfTheSecondVersionIsMigratedWithoutLosingPlays() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("migrate.db")
        file.parentFile?.mkdirs()
        // The first version had no file path, album artist, genre or year.
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL(
                "CREATE TABLE `PlayEvent` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`songUid` TEXT NOT NULL, `title` TEXT NOT NULL, `artists` TEXT NOT NULL, " +
                    "`album` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, " +
                    "`listenedMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL)"
            )
            old.execSQL("CREATE INDEX `index_PlayEvent_startedAt` ON `PlayEvent` (`startedAt`)")
            old.execSQL(
                "INSERT INTO PlayEvent (songUid, title, artists, album, startedAt, listenedMs, " +
                    "durationMs) VALUES ('u', 'Kept', 'A', 'B', 5, 6, 7)"
            )
            old.version = 1
        }
        val migrated =
            Room.databaseBuilder(context, StatsDatabase::class.java, "migrate.db")
                .addMigrations(StatsDatabase.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build()
        try {
            val kept = runBlocking { migrated.playEventDao().all() }.single()
            assertEquals("Kept", kept.title)
            assertEquals("", kept.filePath)
            assertEquals(0, kept.year)
        } finally {
            migrated.close()
        }
    }
}
