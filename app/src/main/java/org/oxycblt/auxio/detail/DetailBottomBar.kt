/*
 * Copyright (c) 2026 Auxio Project
 * DetailBottomBar.kt is part of Auxio.
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

import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.button.MaterialButton
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentDetailBinding
import org.oxycblt.auxio.ui.liftByBottomInset
import org.oxycblt.auxio.util.getDimenPixels
import org.oxycblt.auxio.util.inflater

/**
 * Cancel and Save along the bottom, where the thumb is, while the order of a playlist is edited.
 * The rest of the time the main navigation is there instead (see MainFragment), so the bar is
 * hidden.
 *
 * The top toolbar of the screen is not needed for any of this, so it is collapsed to nothing. It
 * only comes back while songs are selected, as the selection actions are shown there.
 *
 * It is added to the root of the detail layout from code, so the layouts of the other detail
 * screens stay as they are. Use [attach] to create one.
 */
class DetailBottomBar
internal constructor(
    private val frame: ViewGroup,
    private val list: View,
    private val topToolbar: View,
) {
    private val root: View =
        frame.context.inflater.inflate(R.layout.view_detail_bottom_bar, frame, false)
    private val cancel = root.findViewById<MaterialButton>(R.id.bar_cancel)
    private val save = root.findViewById<MaterialButton>(R.id.bar_save)

    var onCancel: () -> Unit = {}
    var onSave: () -> Unit = {}

    init {
        frame.addView(root)
        // The bar sits right above the playback bar and the system navigation, so it is lifted by
        // the bottom inset.
        root.liftByBottomInset()
        setTopToolbarShown(false)

        cancel.setOnClickListener { onCancel() }
        save.setOnClickListener { onSave() }
    }

    /**
     * Show Cancel and Save, or hide the bar again.
     *
     * @param editing Whether the order of a playlist is being edited.
     * @param canSave Whether there is a change that can be saved.
     */
    fun setEditing(editing: Boolean, canSave: Boolean) {
        root.isVisible = editing
        save.isEnabled = canSave
        // The list ends where the bar begins. It has its own bottom padding for the inset, so only
        // the height of the bar is added to its margin.
        val barHeight =
            if (editing) root.context.getDimenPixels(R.dimen.size_detail_bottom_bar) else 0
        list.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = barHeight }
    }

    /**
     * Show the top toolbar, which has the actions for selected songs, or collapse it to nothing.
     */
    fun setTopToolbarShown(shown: Boolean) = setToolbarShown(topToolbar, shown)

    companion object {
        /**
         * Collapse the top toolbar of a detail screen to nothing, or bring it back. Screens whose
         * controls are not in a [DetailBottomBar] but in the main navigation use this too.
         */
        fun setTopToolbarShown(binding: FragmentDetailBinding, shown: Boolean) =
            setToolbarShown(binding.detailToolbar, shown)

        internal fun setToolbarShown(toolbar: View, shown: Boolean) {
            toolbar.updateLayoutParams<ViewGroup.LayoutParams> {
                height = if (shown) ViewGroup.LayoutParams.WRAP_CONTENT else 0
            }
        }

        /**
         * Add the bar to a detail screen.
         *
         * @return The bar, or null if the layout does not support it: the two-pane layout of large
         *   screens keeps its toolbar.
         */
        fun attach(binding: FragmentDetailBinding): DetailBottomBar? {
            if (binding.detailPane != null || binding.root !is ViewGroup) {
                return null
            }
            return DetailBottomBar(
                binding.root as ViewGroup,
                binding.detailRecycler,
                binding.detailToolbar,
            )
        }
    }
}
