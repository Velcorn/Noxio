/*
 * Copyright (c) 2026 Auxio Project
 * MenuGroupDividerTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.list.menu

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.MenuItem
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.R
import org.oxycblt.auxio.list.ClickableListListener
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MenuGroupDividerTest {
    private val context =
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_Auxio_Black,
        )
    private val divider = MenuGroupDivider(context)

    private fun adapter(resource: Int): MenuItemAdapter {
        val menu = MenuBuilder(context)
        SupportMenuInflater(context).inflate(resource, menu)
        return MenuItemAdapter(
                object : ClickableListListener<MenuItem> {
                    override fun onClick(item: MenuItem, viewHolder: RecyclerView.ViewHolder) {}
                },
            )
            .apply { update(menu.children.toList(), null) }
    }

    @Test
    fun playlistDividersSeparateQueueManagementAndDeleteEvenWhenActionsAreDisabled() {
        // The playlist page's menu also has Shuffle at the start of the queue actions and Sort at
        // the start of the playlist actions.
        for ((resource, dividers) in
            listOf(R.menu.playlist to listOf(1, 5), R.menu.detail_playlist to listOf(2, 7))) {
            val last = dividers.last() + 1
            val adapter = adapter(resource)
            // Empty playlists disable queue actions, export and duplicates without changing the
            // groups.
            adapter.currentList.forEach { it.isEnabled = false }
            for (position in 0..last) {
                val expected = position in dividers
                assertEquals(
                    "$resource at $position",
                    expected,
                    divider.shouldDrawDivider(position, adapter),
                )
            }
        }
    }

    @Test
    fun ungroupedMenusAndInvalidPositionsHaveNoDividers() {
        val adapter = adapter(R.menu.song)
        for (position in -1..adapter.itemCount) {
            assertFalse(divider.shouldDrawDivider(position, adapter))
        }
        assertFalse(divider.shouldDrawDivider(0, null))
        assertFalse(divider.isLastItemDecorated)
    }
}
