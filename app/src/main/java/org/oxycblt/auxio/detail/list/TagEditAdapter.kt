/*
 * Copyright (c) 2026 Auxio Project
 * TagEditAdapter.kt is part of Auxio.
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
 
package org.oxycblt.auxio.detail.list

import android.text.InputType
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.ItemTagFieldBinding
import org.oxycblt.auxio.tags.TagField
import org.oxycblt.auxio.util.context
import org.oxycblt.auxio.util.inflater

/**
 * Noxio: the tags of a song as fields to edit, in Song properties. [values] holds what is typed.
 *
 * @author Noxio
 */
class TagEditAdapter(initial: Map<TagField, String>, private val separator: String) :
    RecyclerView.Adapter<TagEditAdapter.ViewHolder>() {
    val values = initial.toMutableMap()
    private var invalid = emptySet<TagField>()

    /** Mark the [fields] that cannot be saved. */
    fun showInvalid(fields: Set<TagField>) {
        invalid = fields
        notifyItemRangeChanged(0, itemCount)
    }

    override fun getItemCount() = TagField.entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemTagFieldBinding.inflate(parent.context.inflater, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(TagField.entries[position])
    }

    inner class ViewHolder(private val binding: ItemTagFieldBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var field: TagField? = null

        init {
            binding.tagValue.doAfterTextChanged { text ->
                field?.let { values[it] = text?.toString() ?: "" }
            }
        }

        fun bind(field: TagField) {
            this.field = null
            val context = binding.context
            binding.tagField.hint = context.getString(labelOf(field))
            binding.tagField.helperText =
                if (field == TagField.ARTIST || field == TagField.ALBUM_ARTIST) {
                    context.getString(R.string.lng_tags_artists, separator)
                } else {
                    null
                }
            binding.tagField.error =
                if (field in invalid) context.getString(R.string.err_tags_field) else null
            binding.tagValue.inputType =
                when (field) {
                    TagField.TRACK -> InputType.TYPE_CLASS_NUMBER
                    TagField.DATE -> InputType.TYPE_CLASS_DATETIME
                    else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                }
            binding.tagValue.setText(values[field] ?: "")
            this.field = field
        }
    }

    private fun labelOf(field: TagField) =
        when (field) {
            TagField.TITLE -> R.string.lbl_name
            TagField.ARTIST -> R.string.lbl_artists
            TagField.ALBUM -> R.string.lbl_album
            TagField.ALBUM_ARTIST -> R.string.lbl_album_artists
            TagField.DATE -> R.string.lbl_date
            TagField.TRACK -> R.string.lbl_track
        }
}
