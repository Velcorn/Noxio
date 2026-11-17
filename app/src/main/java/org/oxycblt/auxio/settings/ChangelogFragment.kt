/*
 * Copyright (c) 2026 Auxio Project
 * ChangelogFragment.kt is part of Auxio.
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
 
package org.oxycblt.auxio.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isNotEmpty
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.transition.MaterialFadeThrough
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentChangelogBinding
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.auxio.ui.liftByBottomInset
import org.oxycblt.auxio.util.systemBarInsetsCompat

/**
 * Shows the changelog of every Noxio release, newest first, read from the copy of `CHANGELOG.md`
 * that is bundled into the app. It is laid out like [AboutFragment]: cards, title bar at the
 * bottom.
 */
@AndroidEntryPoint
class ChangelogFragment : ViewBindingFragment<FragmentChangelogBinding>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
    }

    override fun onCreateBinding(inflater: LayoutInflater) =
        FragmentChangelogBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentChangelogBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.changelogContents.setOnApplyWindowInsetsListener { view, insets ->
            view.updatePadding(top = insets.systemBarInsetsCompat.top)
            insets
        }
        val barHeight = resources.getDimensionPixelSize(R.dimen.size_detail_bottom_bar)
        binding.changelogBar.liftByBottomInset { inset ->
            binding.changelogContents.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = barHeight + inset
            }
        }

        val text = runCatching {
            requireContext().assets.open(Changelog.ASSET).bufferedReader().use {
                it.readText()
            }
        }
            .getOrDefault("")
        for (release in Changelog.parse(text)) {
            binding.changelogList.addView(card(release, binding.changelogList))
        }
    }

    private fun card(release: ChangelogRelease, parent: LinearLayout): MaterialCardView {
        val context = requireContext()
        val medium = resources.getDimensionPixelSize(R.dimen.spacing_medium)
        val small = resources.getDimensionPixelSize(R.dimen.spacing_small)
        val card = MaterialCardView(context)
        card.layoutParams =
            LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                .apply { if (parent.isNotEmpty()) topMargin = medium }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.setPadding(0, 0, 0, small)
        card.addView(column)

        column.addView(
            text(release.version, R.style.Widget_Auxio_TextView_Header, null, 0),
        )
        for (section in release.sections) {
            column.addView(
                text(
                    section.title,
                    0,
                    com.google.android.material.R.attr.textAppearanceTitleSmall,
                    0,
                ),
            )
            for (item in section.items) {
                column.addView(
                    text(
                        "•  $item",
                        0,
                        com.google.android.material.R.attr.textAppearanceBodyMedium,
                        0,
                    ),
                )
            }
        }
        return card
    }

    /** A line of text with the padding of the rows on the About page. */
    private fun text(content: String, style: Int, appearanceAttr: Int?, topPadding: Int): TextView {
        val medium = resources.getDimensionPixelSize(R.dimen.spacing_medium)
        val tiny = resources.getDimensionPixelSize(R.dimen.spacing_tiny)
        val view =
            if (style != 0) TextView(requireContext(), null, 0, style)
            else TextView(requireContext())
        view.text = content
        if (appearanceAttr != null) {
            val typed = android.util.TypedValue()
            requireContext().theme.resolveAttribute(appearanceAttr, typed, true)
            view.setTextAppearance(typed.resourceId)
        }
        if (style == 0) {
            view.setPadding(medium, topPadding + tiny, medium, tiny)
        }
        return view
    }
}
