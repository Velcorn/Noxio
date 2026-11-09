/*
 * Copyright (c) 2026 Auxio Project
 * DetailBottomBarTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.detail

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The bar at the bottom of the detail screens, on the real layout of the screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailBottomBarTest {
    private val context =
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_Auxio_Black,
        )
    // The screen itself cannot be inflated in a test, so its parts are stood in for by plain ones.
    private val frame = FrameLayout(context)
    private val list =
        RecyclerView(context).apply { layoutParams = FrameLayout.LayoutParams(MATCH, MATCH) }
    private val toolbar = View(context).apply { layoutParams = FrameLayout.LayoutParams(MATCH, 56) }
    private val bar: DetailBottomBar

    init {
        frame.addView(list)
        frame.addView(toolbar)
        bar = DetailBottomBar(frame, list, toolbar)
    }

    private fun view(id: Int) = frame.findViewById<View>(id)

    @Test
    fun theBarIsAddedToTheScreenAndItsTopToolbarIsCollapsed() {
        assertNotNull(view(R.id.detail_bottom_bar))
        // The list ends where the bar begins.
        val barHeight = context.resources.getDimensionPixelSize(R.dimen.size_detail_bottom_bar)
        assertEquals(barHeight, (list.layoutParams as FrameLayout.LayoutParams).bottomMargin)
        assertEquals(0, toolbar.layoutParams.height)
    }

    @Test
    fun theToolbarComesBackForSelectionAndGoesAgain() {
        bar.setTopToolbarShown(true)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, toolbar.layoutParams.height)
        bar.setTopToolbarShown(false)
        assertEquals(0, toolbar.layoutParams.height)
    }

    @Test
    fun theSortButtonIsOnlyThereWhereTheListCanBeSorted() {
        assertFalse(view(R.id.bar_sort).isVisible())
        bar.setSortAvailable(true)
        assertTrue(view(R.id.bar_sort).isVisible())
    }

    @Test
    fun playingIsDisabledAndFadedWhileThereIsNothingToPlay() {
        bar.setPlayable(false)
        assertFalse(view(R.id.bar_play).isEnabled)
        assertFalse(view(R.id.bar_shuffle).isEnabled)
        assertEquals(0.38f, view(R.id.bar_play).alpha, 0.001f)
        bar.setPlayable(true)
        assertTrue(view(R.id.bar_play).isEnabled)
        assertEquals(1f, view(R.id.bar_shuffle).alpha, 0f)
    }

    @Test
    fun editingSwapsTheControlsForCancelAndSave() {
        bar.setSortAvailable(true)
        bar.setEditing(editing = true, canSave = false)
        assertFalse(view(R.id.bar_back).isVisible())
        assertFalse(view(R.id.bar_sort).isVisible())
        assertFalse(view(R.id.bar_more).isVisible())
        assertTrue(view(R.id.bar_cancel).isVisible())
        assertTrue(view(R.id.bar_save).isVisible())
        assertFalse(view(R.id.bar_save).isEnabled)

        bar.setEditing(editing = true, canSave = true)
        assertTrue(view(R.id.bar_save).isEnabled)

        bar.setEditing(editing = false, canSave = false)
        assertTrue(view(R.id.bar_back).isVisible())
        assertTrue(view(R.id.bar_sort).isVisible())
        assertFalse(view(R.id.bar_cancel).isVisible())
    }

    @Test
    fun theButtonsReportTheirClicks() {
        var clicked = mutableListOf<String>()
        bar.onBack = { clicked += "back" }
        bar.onPlay = { clicked += "play" }
        bar.onShuffle = { clicked += "shuffle" }
        bar.onMore = { clicked += "more" }
        bar.onSort = { clicked += "sort" }
        bar.onCancel = { clicked += "cancel" }
        bar.onSave = { clicked += "save" }
        listOf(
                R.id.bar_back,
                R.id.bar_play,
                R.id.bar_shuffle,
                R.id.bar_more,
                R.id.bar_sort,
                R.id.bar_cancel,
                R.id.bar_save,
            )
            .forEach { view(it).performClick() }
        assertEquals(
            listOf("back", "play", "shuffle", "more", "sort", "cancel", "save"),
            clicked,
        )
    }

    private fun View.isVisible() = visibility == View.VISIBLE

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
