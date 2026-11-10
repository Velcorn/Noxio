/*
 * Copyright (c) 2026 Auxio Project
 * MenuGroupDivider.kt is part of Auxio.
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
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDividerItemDecoration
import org.oxycblt.auxio.R

/** Separates action groups in item menus, leaving ungrouped menus without dividers. */
class MenuGroupDivider(context: Context) : MaterialDividerItemDecoration(context, VERTICAL) {
    init {
        isLastItemDecorated = false
        setDividerInsetStartResource(context, R.dimen.spacing_medium)
        setDividerInsetEndResource(context, R.dimen.spacing_medium)
    }

    public override fun shouldDrawDivider(
        position: Int,
        adapter: RecyclerView.Adapter<*>?,
    ): Boolean {
        val menu = adapter as? MenuItemAdapter ?: return false
        if (position < 0 || position >= menu.itemCount - 1) return false
        return menu.getItem(position).groupId != menu.getItem(position + 1).groupId
    }
}
