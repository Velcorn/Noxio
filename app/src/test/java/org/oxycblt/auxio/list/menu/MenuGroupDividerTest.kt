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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
                }
            )
            .apply { update(menu.children.toList(), null) }
    }

    @Test
    fun playlistDividersSeparateQueueManagementAndDeleteEvenWhenActionsAreDisabled() {
        for (resource in listOf(R.menu.playlist, R.menu.detail_playlist)) {
            val adapter = adapter(resource)
            // Empty playlists disable queue actions, export and duplicates without changing the
            // groups.
            adapter.currentList.forEach { it.isEnabled = false }
            assertFalse(divider.shouldDrawDivider(0, adapter))
            assertTrue(divider.shouldDrawDivider(1, adapter))
            assertFalse(divider.shouldDrawDivider(2, adapter))
            assertFalse(divider.shouldDrawDivider(3, adapter))
            assertFalse(divider.shouldDrawDivider(4, adapter))
            assertTrue(divider.shouldDrawDivider(5, adapter))
            assertFalse(divider.shouldDrawDivider(6, adapter))
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
