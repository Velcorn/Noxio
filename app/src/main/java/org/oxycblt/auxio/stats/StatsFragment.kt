/*
 * Copyright (c) 2026 Auxio Project
 * StatsFragment.kt is part of Auxio.
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
 
package org.oxycblt.auxio.stats

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialFadeThrough
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentStatsBinding
import org.oxycblt.auxio.databinding.ItemStatsRowBinding
import org.oxycblt.auxio.ui.HorizontalSwipeDetector
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.auxio.ui.blackenWindowOnShow
import org.oxycblt.auxio.ui.liftByBottomInset
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.showToast
import org.oxycblt.auxio.util.systemBarInsetsCompat

/**
 * Shows the listening history of a week, month or year: total time and the top artists, albums and
 * songs. One category is shown at a time as a colored card that can be shared as an image.
 */
@AndroidEntryPoint
class StatsFragment : ViewBindingFragment<FragmentStatsBinding>() {
    private val statsModel: StatsViewModel by viewModels()
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) {
                export(uri)
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                import(uris)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
    }

    override fun onCreateBinding(inflater: LayoutInflater) = FragmentStatsBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentStatsBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.statsBack.setOnClickListener { findNavController().navigateUp() }
        binding.statsMenu.setOnClickListener { view ->
            PopupMenu(requireContext(), view).apply {
                menuInflater.inflate(R.menu.toolbar_stats, menu)
                setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.action_export_stats -> {
                            exportLauncher.launch("noxio-stats-${LocalDate.now()}.csv")
                            true
                        }
                        R.id.action_import_stats -> {
                            importLauncher.launch(arrayOf("*/*"))
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }
        // The categories are at the top, under the status bar, and the bar at the bottom is lifted
        // above the navigation.
        binding.statsContents.setOnApplyWindowInsetsListener { _, insets ->
            binding.statsPill.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin =
                    insets.systemBarInsetsCompat.top +
                        resources.getDimensionPixelSize(R.dimen.spacing_small)
            }
            insets
        }
        binding.statsBar.liftByBottomInset()
        setupSwipeBetweenCategories(binding)

        binding.statsPeriodGroup.check(
            when (statsModel.query.value.period) {
                StatsPeriod.WEEK -> R.id.stats_week
                StatsPeriod.MONTH -> R.id.stats_month
                StatsPeriod.YEAR -> R.id.stats_year
                StatsPeriod.ALL -> R.id.stats_all
            }
        )
        binding.statsPeriodGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                statsModel.setPeriod(
                    when (checkedId) {
                        R.id.stats_month -> StatsPeriod.MONTH
                        R.id.stats_year -> StatsPeriod.YEAR
                        R.id.stats_all -> StatsPeriod.ALL
                        else -> StatsPeriod.WEEK
                    }
                )
            }
        }
        binding.statsCategoryGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                statsModel.setCategory(
                    when (checkedId) {
                        R.id.stats_albums -> StatsCategory.ALBUMS
                        R.id.stats_songs -> StatsCategory.SONGS
                        else -> StatsCategory.ARTISTS
                    }
                )
            }
        }
        binding.statsPrev.setOnClickListener { statsModel.previous() }
        binding.statsNext.setOnClickListener { statsModel.next() }
        binding.statsMore.setOnClickListener { statsModel.toggleExpanded() }
        binding.statsShare.setOnClickListener {
            StatsShareDialog().show(childFragmentManager, StatsShareDialog.TAG)
        }

        collectImmediately(statsModel.ui) { render() }
        collectImmediately(statsModel.category) { render() }
        collectImmediately(statsModel.expanded) { render() }
    }

    /** A swipe to the left or right moves between artists, albums and songs. */
    private fun setupSwipeBetweenCategories(binding: FragmentStatsBinding) {
        val detector =
            HorizontalSwipeDetector(requireContext()) { forward ->
                neighbourCategory(statsModel.category.value, forward)?.let(statsModel::setCategory)
            }
        // Reading the touches does not take them from the scrolling.
        @SuppressLint("ClickableViewAccessibility")
        binding.statsContents.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
    }

    private fun render() {
        val ui = statsModel.ui.value ?: return
        val binding = requireBinding()
        val context = requireContext()
        val stats = ui.stats
        val category = statsModel.category.value
        val items = statsModel.items(ui)
        val expanded = statsModel.expanded.value

        binding.statsRange.text = StatsFormat.range(context, ui.query.period, ui.range)
        binding.statsNext.isEnabled = ui.query.offset < 0
        // All time has nothing before or after it.
        val navVisibility = if (ui.query.period == StatsPeriod.ALL) View.INVISIBLE else View.VISIBLE
        binding.statsPrev.visibility = navVisibility
        binding.statsNext.visibility = navVisibility
        updatePill(category)

        val hasPlays = !stats.isEmpty
        binding.statsEmpty.isVisible = !hasPlays
        binding.statsHero.isVisible = hasPlays
        binding.statsCard.isVisible = hasPlays
        binding.statsMore.isVisible = hasPlays && items.size > COLLAPSED_ROWS
        binding.statsHeroTime.text = StatsFormat.listened(context, stats.listenedMs)
        binding.statsHeroPlays.text = StatsFormat.plays(context, stats.plays)
        binding.statsMore.setText(
            if (expanded) R.string.lbl_stats_show_five else R.string.lbl_stats_show_ten
        )

        binding.statsCard.setBackgroundResource(
            when (category) {
                StatsCategory.ARTISTS -> R.drawable.bg_stats_artists
                StatsCategory.ALBUMS -> R.drawable.bg_stats_albums
                StatsCategory.SONGS -> R.drawable.bg_stats_songs
            }
        )
        binding.statsCardTitle.setText(
            when (category) {
                StatsCategory.ARTISTS -> R.string.lbl_stats_top_artists
                StatsCategory.ALBUMS -> R.string.lbl_stats_top_albums
                StatsCategory.SONGS -> R.string.lbl_stats_top_songs
            }
        )

        binding.statsList.removeAllViews()
        val inflater = LayoutInflater.from(context)
        items.take(if (expanded) EXPANDED_ROWS else COLLAPSED_ROWS).forEachIndexed { index, item ->
            val row = ItemStatsRowBinding.inflate(inflater, binding.statsList, false)
            row.statsRank.text = String.format(Locale.getDefault(), "%d", index + 1)
            if (index == 0) {
                row.statsRank.setBackgroundResource(R.drawable.bg_stats_rank_first)
                row.statsRank.setTextColor("#222222".toColorInt())
            }
            row.statsName.text = item.name
            row.statsSubtitle.text = item.subtitle
            row.statsSubtitle.isVisible = !item.subtitle.isNullOrEmpty()
            row.statsCount.text =
                getString(
                    R.string.fmt_stats_row,
                    StatsFormat.plays(context, item.plays),
                    StatsFormat.listened(context, item.listenedMs),
                )
            binding.statsList.addView(row.root)
        }
    }

    /** Color the selected category in the pill with its own gradient color. */
    private fun updatePill(category: StatsCategory) {
        val binding = requireBinding()
        val context = requireContext()
        val buttons =
            listOf(
                StatsCategory.ARTISTS to binding.statsArtists,
                StatsCategory.ALBUMS to binding.statsAlbums,
                StatsCategory.SONGS to binding.statsSongs,
            )
        val checked = buttons.first { it.first == category }.second
        if (binding.statsCategoryGroup.checkedButtonId != checked.id) {
            binding.statsCategoryGroup.check(checked.id)
        }
        val onSurface =
            MaterialColors.getColor(
                binding.statsPill,
                com.google.android.material.R.attr.colorOnSurface,
            )
        for ((buttonCategory, button) in buttons) {
            val selected = buttonCategory == category
            button.backgroundTintList =
                ColorStateList.valueOf(
                    if (selected) StatsImage.gradientOf(context, buttonCategory).first
                    else Color.TRANSPARENT
                )
            button.setTextColor(if (selected) Color.WHITE else onSurface)
        }
    }

    private fun import(uris: List<Uri>) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val parsed =
                withContext(Dispatchers.IO) {
                    uris.map { readText(context, it)?.let(StatsCsvImport::parse) }
                }
            val files = parsed.filterNotNull()
            when {
                files.isEmpty() || files.size != parsed.size ->
                    context.showToast(R.string.err_stats_import)
                // A most-played list has no dates, so it can only count towards all time.
                files.any { it is StatsCsvImport.Parsed.MostPlayed } -> confirmBaseline(files)
                else -> finishImport(files)
            }
        }
    }

    private fun confirmBaseline(files: List<StatsCsvImport.Parsed>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lbl_stats_import_baseline)
            .setMessage(R.string.lbl_stats_import_baseline_desc)
            .setPositiveButton(R.string.lbl_ok) { _, _ -> finishImport(files) }
            .setNegativeButton(R.string.lbl_cancel, null)
            .create()
            .apply { blackenWindowOnShow() }
            .show()
    }

    private fun finishImport(files: List<StatsCsvImport.Parsed>) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val added = withContext(Dispatchers.IO) { statsModel.importCsv(files) }
            Toast.makeText(
                    context,
                    context.resources.getQuantityString(R.plurals.fmt_stats_imported, added, added),
                    Toast.LENGTH_SHORT,
                )
                .show()
        }
    }

    private fun readText(context: Context, uri: Uri): String? =
        try {
            context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        } catch (e: IOException) {
            null
        }

    private fun export(uri: Uri) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val exported =
                withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri, "wt")?.use {
                            statsModel.exportCsv(it)
                        } != null
                    } catch (e: IOException) {
                        false
                    }
                }
            context.showToast(
                if (exported) R.string.lbl_stats_exported else R.string.err_stats_export
            )
        }
    }

    private companion object {
        const val COLLAPSED_ROWS = 5
        const val EXPANDED_ROWS = 10
    }
}
