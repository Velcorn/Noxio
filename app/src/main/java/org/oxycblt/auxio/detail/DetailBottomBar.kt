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
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.button.MaterialButton
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentDetailBinding
import org.oxycblt.auxio.util.getDimenPixels
import org.oxycblt.auxio.util.inflater
import org.oxycblt.auxio.util.systemBarInsetsCompat

/**
 * The controls of a detail screen along the bottom, where the thumb is: back, the name, play,
 * shuffle and more. While the order of a playlist is edited they are replaced by Cancel and Save.
 *
 * The top toolbar of the screen is not needed for any of this, so it is collapsed to nothing. It
 * only comes back while songs are selected, as the selection actions are shown there.
 *
 * It is added to the root of the detail layout from code, so the layouts of the other detail
 * screens stay as they are. Use [attach] to create one.
 */
class DetailBottomBar private constructor(private val binding: FragmentDetailBinding) {
    private val root: View =
        binding.root.context.inflater.inflate(
            R.layout.view_detail_bottom_bar,
            binding.root as ViewGroup,
            false,
        )
    private val back = root.findViewById<ImageButton>(R.id.bar_back)
    private val title = root.findViewById<TextView>(R.id.bar_title)
    private val play = root.findViewById<ImageButton>(R.id.bar_play)
    private val shuffle = root.findViewById<ImageButton>(R.id.bar_shuffle)
    private val more = root.findViewById<ImageButton>(R.id.bar_more)
    private val cancel = root.findViewById<MaterialButton>(R.id.bar_cancel)
    private val spacer = root.findViewById<View>(R.id.bar_spacer)
    private val save = root.findViewById<MaterialButton>(R.id.bar_save)

    var onBack: () -> Unit = {}
    var onPlay: () -> Unit = {}
    var onShuffle: () -> Unit = {}
    var onMore: () -> Unit = {}
    var onCancel: () -> Unit = {}
    var onSave: () -> Unit = {}

    init {
        val frame = binding.root as ViewGroup
        frame.addView(root)

        val barHeight = root.context.getDimenPixels(R.dimen.size_detail_bottom_bar)
        // The bar sits right above the playback bar and the system navigation, so it is lifted by
        // the bottom inset. The list ends where the bar begins: it has its own bottom padding for
        // the inset, so only the height of the bar is added to its margin.
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.systemBarInsetsCompat.bottom
            }
            insets
        }
        binding.detailRecycler.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = barHeight
        }
        setTopToolbarShown(false)

        back.setOnClickListener { onBack() }
        play.setOnClickListener { onPlay() }
        shuffle.setOnClickListener { onShuffle() }
        more.setOnClickListener { onMore() }
        cancel.setOnClickListener { onCancel() }
        save.setOnClickListener { onSave() }
    }

    /** Show the name of what the screen is about. */
    fun setTitle(name: CharSequence) {
        title.text = name
    }

    /** Whether there is anything to play. Playing is not possible while editing either. */
    fun setPlayable(playable: Boolean) {
        play.isEnabled = playable
        shuffle.isEnabled = playable
        play.alpha = if (playable) 1f else DISABLED_ALPHA
        shuffle.alpha = if (playable) 1f else DISABLED_ALPHA
    }

    /**
     * Switch between the normal controls and Cancel and Save.
     *
     * @param editing Whether the order of a playlist is being edited.
     * @param canSave Whether there is a change that can be saved.
     */
    fun setEditing(editing: Boolean, canSave: Boolean) {
        for (view in listOf(back, title, play, shuffle, more)) {
            view.isVisible = !editing
        }
        cancel.isVisible = editing
        spacer.isVisible = editing
        save.isVisible = editing
        save.isEnabled = canSave
    }

    /**
     * Show the top toolbar, which has the actions for selected songs, or collapse it to nothing.
     */
    fun setTopToolbarShown(shown: Boolean) = setTopToolbarShown(binding, shown)

    companion object {
        private const val DISABLED_ALPHA = 0.38f

        /**
         * Collapse the top toolbar of a detail screen to nothing, or bring it back. Screens whose
         * controls are not in a [DetailBottomBar] but in the main navigation use this too.
         */
        fun setTopToolbarShown(binding: FragmentDetailBinding, shown: Boolean) {
            binding.detailToolbar.updateLayoutParams<ViewGroup.LayoutParams> {
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
            return DetailBottomBar(binding)
        }
    }
}
