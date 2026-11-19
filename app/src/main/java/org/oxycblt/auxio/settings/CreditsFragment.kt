/*
 * Copyright (c) 2026 Auxio Project
 * CreditsFragment.kt is part of Auxio.
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
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isNotEmpty
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialFadeThrough
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentCreditsBinding
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.auxio.ui.liftByBottomInset
import org.oxycblt.auxio.util.openInBrowser
import org.oxycblt.auxio.util.systemBarInsetsCompat

/**
 * The credits under About > Licenses: Noxio, what it is built on and every library it ships, each
 * with its license. A tap on an entry shows the license text, which is bundled, so this works
 * offline. Laid out like [ChangelogFragment].
 */
@AndroidEntryPoint
class CreditsFragment : ViewBindingFragment<FragmentCreditsBinding>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
    }

    override fun onCreateBinding(inflater: LayoutInflater) =
        FragmentCreditsBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentCreditsBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.creditsContents.setOnApplyWindowInsetsListener { view, insets ->
            view.updatePadding(top = insets.systemBarInsetsCompat.top)
            insets
        }
        val barHeight = resources.getDimensionPixelSize(R.dimen.size_detail_bottom_bar)
        binding.creditsBar.liftByBottomInset { inset ->
            binding.creditsContents.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = barHeight + inset
            }
        }

        val credits = runCatching {
            resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use {
                CreditsList.parse(it.readText())
            }
        }
            .getOrDefault(Credits(emptyList(), emptyList()))
        val list = binding.creditsList
        list.addView(card(getString(R.string.lbl_credits_foundations), credits.foundations, list))
        list.addView(card(getString(R.string.lbl_credits_libraries), credits.libraries, list))
    }

    private fun card(title: String, credits: List<Credit>, parent: LinearLayout): MaterialCardView {
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
            TextView(context, null, 0, R.style.Widget_Auxio_TextView_Header).apply {
                text = title
            },
        )
        for (credit in credits) {
            column.addView(row(credit))
        }
        return card
    }

    /** Name, then author and license below it, tappable for the license text. */
    private fun row(credit: Credit): LinearLayout {
        val context = requireContext()
        val medium = resources.getDimensionPixelSize(R.dimen.spacing_medium)
        val small = resources.getDimensionPixelSize(R.dimen.spacing_small)
        val row = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        row.setPadding(medium, small, medium, small)
        val ripple = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
        row.setBackgroundResource(ripple.resourceId)
        row.addView(
            text(credit.name, com.google.android.material.R.attr.textAppearanceBodyLarge, false),
        )
        val details = listOf(credit.by, credit.licenses.joinToString(", ") { it.name })
        row.addView(
            text(
                details.filter { it.isNotBlank() }.joinToString(" · "),
                com.google.android.material.R.attr.textAppearanceBodyMedium,
                true,
            ),
        )
        row.setOnClickListener { showLicense(credit) }
        return row
    }

    private fun text(content: String, appearanceAttr: Int, secondary: Boolean): TextView {
        val view = TextView(requireContext())
        val typed = TypedValue()
        requireContext().theme.resolveAttribute(appearanceAttr, typed, true)
        view.setTextAppearance(typed.resourceId)
        if (secondary) {
            requireContext().theme.resolveAttribute(android.R.attr.textColorSecondary, typed, true)
            view.setTextColor(requireContext().getColorStateList(typed.resourceId))
        }
        view.text = content
        return view
    }

    private fun showLicense(credit: Credit) {
        val message =
            credit.licenses.joinToString("\n\n") { license ->
                license.text.ifEmpty { listOfNotNull(license.name, license.url).joinToString("\n") }
            }
        val dialog =
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(credit.name)
                .setMessage(message)
                .setPositiveButton(R.string.lbl_ok, null)
        credit.website?.let { url ->
            dialog.setNeutralButton(R.string.lbl_website) { _, _ ->
                requireContext().openInBrowser(url)
            }
        }
        dialog.show()
    }
}
