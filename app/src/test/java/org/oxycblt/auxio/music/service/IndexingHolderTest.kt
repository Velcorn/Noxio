/*
 * Copyright (c) 2026 Auxio Project
 * IndexingHolderTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.music.MusicSettings
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IndexingHolderTest {
    @Test
    fun disabledAutomaticReloadingNeverStartsAWatcherButSourceChangesStillRefresh() {
        val settings = mockk<MusicSettings>(relaxed = true)
        every { settings.shouldBeObserving } returns false
        val repository = mockk<MusicRepository>(relaxed = true)
        val holder =
            IndexingHolder.Factory(
                    mockk(relaxed = true),
                    repository,
                    settings,
                    mockk(relaxed = true),
                )
                .create(ApplicationProvider.getApplicationContext<Context>(), mockk(relaxed = true))
        holder.attach()
        try {
            holder.onMusicLocationsChanged()
            // Resolving a filesystem is the first step toward registering a watcher.
            verify(exactly = 0) { settings.locationMode }
            verify(exactly = 0) { settings.safQuery }
            verify(exactly = 0) { settings.mediaStoreQuery }
            verify(exactly = 1) { repository.requestIndex(true) }
        } finally {
            holder.release()
        }
    }
}
