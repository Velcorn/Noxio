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

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialFadeThrough
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentStatsBinding
import org.oxycblt.auxio.databinding.ItemStatsRowBinding
import org.oxycblt.auxio.ui.ViewBindingFragment
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

    private var pendingMostPlayedRange: LongRange? = null
    private val exportMostPlayedLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            val range = pendingMostPlayedRange
            if (uri != null && range != null) {
                exportMostPlayed(uri, range)
            }
        }
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                import(uri)
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

        binding.statsToolbar.apply {
            setNavigationOnClickListener { findNavController().navigateUp() }
            setOnMenuItemClickListener(
                Toolbar.OnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.action_export_stats -> {
                            exportLauncher.launch("noxio-stats-${LocalDate.now()}.csv")
                            true
                        }
                        R.id.action_export_most_played -> {
                            statsModel.ui.value?.let { ui ->
                                pendingMostPlayedRange = ui.range
                                val context = requireContext()
                                val label = StatsFormat.range(context, ui.query.period, ui.range)
                                exportMostPlayedLauncher.launch("Most played Songs - $label.csv")
                            }
                            true
                        }
                        R.id.action_import_stats -> {
                            importLauncher.launch(arrayOf("*/*"))
                            true
                        }
                        else -> false
                    }
                }
            )
        }
        binding.statsContents.setOnApplyWindowInsetsListener { view, insets ->
            view.updatePadding(bottom = insets.systemBarInsetsCompat.bottom)
            insets
        }
        binding.statsPill.setOnApplyWindowInsetsListener { view, insets ->
            view.updatePadding(bottom = insets.systemBarInsetsCompat.bottom)
            insets
        }

        binding.statsPeriodGroup.check(
            when (statsModel.query.value.period) {
                StatsPeriod.WEEK -> R.id.stats_week
                StatsPeriod.MONTH -> R.id.stats_month
                StatsPeriod.YEAR -> R.id.stats_year
            }
        )
        binding.statsPeriodGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                statsModel.setPeriod(
                    when (checkedId) {
                        R.id.stats_month -> StatsPeriod.MONTH
                        R.id.stats_year -> StatsPeriod.YEAR
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
            row.statsRank.text = (index + 1).toString()
            if (index == 0) {
                row.statsRank.setBackgroundResource(R.drawable.bg_stats_rank_first)
                row.statsRank.setTextColor(Color.parseColor("#222222"))
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

    private fun exportMostPlayed(uri: Uri, range: LongRange) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val exported =
                withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri, "wt")?.use {
                            statsModel.exportMostPlayedCsv(range, it)
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

    private fun import(uri: Uri) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val (parsed, name) =
                withContext(Dispatchers.IO) {
                    readText(context, uri)?.let(StatsCsvImport::parse) to displayName(context, uri)
                }
            when (parsed) {
                null -> context.showToast(R.string.err_stats_import)
                is StatsCsvImport.Parsed.History -> finishImport(parsed, null)
                is StatsCsvImport.Parsed.MostPlayed -> askMonth(parsed, name)
            }
        }
    }

    /** A most-played list has no dates, so ask which month it is from. */
    private fun askMonth(parsed: StatsCsvImport.Parsed.MostPlayed, fileName: String?) {
        val context = requireContext()
        val months =
            (0 until MONTH_CHOICES).map {
                Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -it)
                }
            }
        val labels = months.map {
            DateUtils.formatDateTime(
                context,
                it.timeInMillis,
                DateUtils.FORMAT_SHOW_DATE or
                    DateUtils.FORMAT_NO_MONTH_DAY or
                    DateUtils.FORMAT_SHOW_YEAR,
            )
        }
        // Musicolet puts the month in the name of the file, like "Most played Songs - October
        // 2026". Without it, the last month is the most likely one.
        val named = fileName?.let { monthInName(it) }
        var selected =
            months
                .indexOfFirst {
                    named != null &&
                        it.get(Calendar.YEAR) == named.first &&
                        it.get(Calendar.MONTH) == named.second
                }
                .takeIf { it >= 0 } ?: 1
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.lbl_stats_import_month)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { _, which -> selected = which }
            .setPositiveButton(R.string.lbl_ok) { _, _ ->
                val month = months[selected]
                finishImport(
                    parsed,
                    StatsCsvImport.monthRange(month.get(Calendar.YEAR), month.get(Calendar.MONTH)),
                )
            }
            .setNegativeButton(R.string.lbl_cancel, null)
            .show()
    }

    private fun finishImport(parsed: StatsCsvImport.Parsed, month: LongRange?) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val added = withContext(Dispatchers.IO) { statsModel.importCsv(parsed, month) }
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

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }

    /** Finds "October 2026" (in English) in a file name, as year and zero-based month. */
    private fun monthInName(name: String): Pair<Int, Int>? {
        fun english(month: Month) = month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val names = Month.values().joinToString("|") { english(it) }
        val match = Regex("($names)\\W+(\\d{4})", RegexOption.IGNORE_CASE).find(name) ?: return null
        val month = Month.values().first { english(it).equals(match.groupValues[1], true) }
        return match.groupValues[2].toInt() to month.ordinal
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
        const val MONTH_CHOICES = 36
        const val COLLAPSED_ROWS = 5
        const val EXPANDED_ROWS = 10
    }
}
