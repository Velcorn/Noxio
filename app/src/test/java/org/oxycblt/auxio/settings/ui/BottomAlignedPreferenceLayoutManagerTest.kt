/*
 * Copyright (c) 2026 Auxio Project
 * BottomAlignedPreferenceLayoutManagerTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.settings.ui

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BottomAlignedPreferenceLayoutManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = BottomAlignedPreferenceLayoutManager(context)
    private val rows =
        object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            var count = 3

            override fun getItemCount() = count

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                object :
                    RecyclerView.ViewHolder(
                        View(context).apply {
                            layoutParams =
                                RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 80)
                        }
                    ) {}

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
        }
    private val list =
        RecyclerView(context).apply {
            layoutManager = manager
            adapter = rows
            itemAnimator = null
            setPadding(0, 20, 0, 40)
            // Preference headers add decoration height, which must count toward the fit.
            addItemDecoration(
                object : RecyclerView.ItemDecoration() {
                    override fun getItemOffsets(
                        outRect: Rect,
                        view: View,
                        parent: RecyclerView,
                        state: RecyclerView.State,
                    ) {
                        outRect.bottom = 10
                    }
                }
            )
        }

    private fun layout(height: Int = 600) {
        list.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        list.layout(0, 0, 400, height)
    }

    private fun top() = manager.getDecoratedTop(manager.findViewByPosition(0)!!)

    private fun bottom() = manager.getDecoratedBottom(manager.findViewByPosition(rows.count - 1)!!)

    @Test
    fun shortListsRestAboveTheBottomInsetAndDoNotDriftOnRelayout() {
        layout()
        assertEquals(560, bottom())
        assertEquals(290, top())
        list.requestLayout()
        layout()
        assertEquals(560, bottom())
        assertEquals(290, top())
    }

    @Test
    fun longListsStartAtTheTopAndRemainScrollable() {
        rows.count = 20
        layout()
        assertEquals(20, top())
        list.scrollBy(0, 200)
        assertTrue(manager.findFirstVisibleItemPosition() > 0)
    }

    @Test
    fun growingAndShrinkingListsRecalculateTheAlignment() {
        layout()
        rows.count = 20
        rows.notifyDataSetChanged()
        layout()
        assertEquals(20, top())
        rows.count = 2
        rows.notifyDataSetChanged()
        layout()
        assertEquals(560, bottom())
    }

    @Test
    fun resizingKeepsFittingListsAtTheBottomAndLongListsAtTheTop() {
        layout()
        layout(300)
        assertEquals(20, top())
        layout(800)
        assertEquals(760, bottom())
    }

    @Test
    fun emptyAndExactlyFittingListsHaveNoExtraGap() {
        rows.count = 0
        layout()
        assertEquals(0, list.childCount)
        rows.count = 6
        rows.notifyDataSetChanged()
        layout()
        assertEquals(20, top())
        assertEquals(560, bottom())
    }
}
