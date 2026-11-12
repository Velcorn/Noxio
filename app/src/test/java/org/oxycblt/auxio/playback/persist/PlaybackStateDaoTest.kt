/*
 * Copyright (c) 2026 Auxio Project
 * PlaybackStateDaoTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.persist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.playback.state.RepeatMode
import org.oxycblt.musikr.Music
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The queries of the saved playback state on a real (in memory) database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackStateDaoTest {
    private val database =
        Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                PersistenceDatabase::class.java,
            )
            .allowMainThreadQueries()
            .build()
    private val dao = database.playbackStateDao()

    @After fun close() = database.close()

    private fun state(positionMs: Long) =
        PlaybackState(
            id = 0,
            index = 4,
            positionMs = positionMs,
            repeatMode = RepeatMode.ALL,
            songUid = Music.UID.fromString("uas00000000-0000-0000-0000-00000000000a")!!,
            parentUid = null,
        )

    @Test
    fun onlyThePositionChanges() = runBlocking {
        dao.insertState(state(1_000))
        dao.updatePosition(95_000)
        val saved = dao.getState()!!
        assertEquals(95_000L, saved.positionMs)
        assertEquals(state(95_000), saved)
        assertEquals(4, saved.index)
        assertEquals(RepeatMode.ALL, saved.repeatMode)
    }

    @Test
    fun withoutASavedStateThereIsNothingToChange() = runBlocking {
        dao.updatePosition(5_000)
        assertNull(dao.getState())
    }

    @Test
    fun theLastPositionWins() = runBlocking {
        dao.insertState(state(0))
        dao.updatePosition(10_000)
        dao.updatePosition(25_000)
        assertEquals(25_000L, dao.getState()!!.positionMs)
    }
}
