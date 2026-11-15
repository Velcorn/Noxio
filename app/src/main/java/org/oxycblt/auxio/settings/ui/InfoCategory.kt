/*
 * Copyright (c) 2026 Auxio Project
 * InfoCategory.kt is part of Auxio.
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
import android.util.AttributeSet
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.oxycblt.auxio.R

/**
 * A [PreferenceCategory] with a small "i" at the right end of its header. It explains the whole
 * group, so the rows in it can have short summaries. Tapping the "i" shows a dialog with the
 * `infoTitle` and `infoMessage` set in the XML.
 */
class InfoCategory
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceCategoryStyle,
) : PreferenceCategory(context, attrs, defStyleAttr) {
    private var infoTitle = 0
    private var infoMessage = 0

    init {
        layoutResource = R.layout.item_header_info
        context.obtainStyledAttributes(attrs, R.styleable.InfoCategory).use {
            infoTitle = it.getResourceId(R.styleable.InfoCategory_infoTitle, 0)
            infoMessage = it.getResourceId(R.styleable.InfoCategory_infoMessage, 0)
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.findViewById(R.id.info_button)?.setOnClickListener {
            if (infoMessage != 0) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(infoTitle)
                    .setMessage(infoMessage)
                    .setPositiveButton(R.string.lbl_ok, null)
                    .show()
            }
        }
    }
}
