/*
 * Copyright (c) 2026 Auxio Project
 * StatsImageTest.kt is part of Auxio.
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
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsImageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun content(rows: Int, category: StatsCategory = StatsCategory.ARTISTS) =
        StatsImageContent(
            category = category,
            title = "Top artists",
            rangeLabel = "November 2026",
            rows =
                List(rows) {
                    StatsImageContent.Row("Artist $it", "Album $it", "${it + 1} plays", "$it min")
                },
            totalTime = "4 h 25 min",
            totalPlays = "80 plays",
        )

    @Test
    fun theImageHasTheSameSizeOnEveryScreen() {
        val bitmap = StatsImage.render(context, content(rows = 5))
        assertEquals(StatsImage.WIDTH, bitmap.width)
        assertEquals(StatsImage.HEIGHT, bitmap.height)
    }

    @Test
    fun anEmptyListAndAVeryLongNameStillRender() {
        assertEquals(StatsImage.WIDTH, StatsImage.render(context, content(rows = 0)).width)
        val long =
            content(rows = 1)
                .copy(
                    rows = listOf(StatsImageContent.Row("N".repeat(500), null, "1 play", "1 min"))
                )
        assertEquals(StatsImage.HEIGHT, StatsImage.render(context, long).height)
    }

    @Test
    fun everyCategoryHasItsOwnColors() {
        val colors = StatsCategory.entries.map { StatsImage.gradientOf(context, it) }
        assertEquals(StatsCategory.entries.size, colors.toSet().size)
        colors.forEach { (start, end) -> assertNotEquals(start, end) }
    }
}
