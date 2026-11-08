/*
 * Copyright (c) 2026 Noxio Project
 * StatsFragment.kt is part of Noxio.
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

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.transition.MaterialFadeThrough
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentStatsBinding
import org.oxycblt.auxio.databinding.ItemStatsRowBinding
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.systemBarInsetsCompat

/**
 * Shows the listening history of a week, month or year: total time and the top songs, artists and
 * albums.
 */
@AndroidEntryPoint
class StatsFragment : ViewBindingFragment<FragmentStatsBinding>() {
    private val statsModel: StatsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
    }

    override fun onCreateBinding(inflater: LayoutInflater) = FragmentStatsBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentStatsBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.statsToolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.statsContents.setOnApplyWindowInsetsListener { view, insets ->
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
        binding.statsPrev.setOnClickListener { statsModel.previous() }
        binding.statsNext.setOnClickListener { statsModel.next() }

        collectImmediately(statsModel.ui, ::updateUi)
    }

    private fun updateUi(ui: StatsViewModel.Ui?) {
        if (ui == null) {
            return
        }
        val binding = requireBinding()
        val stats = ui.stats

        binding.statsRange.text = formatRange(ui.query.period, ui.range)
        binding.statsNext.isEnabled = ui.query.offset < 0

        binding.statsEmpty.isVisible = stats.isEmpty
        binding.statsSummary.isVisible = !stats.isEmpty
        binding.statsSummaryPlays.isVisible = !stats.isEmpty
        binding.statsSummary.text = formatListened(stats.listenedMs)
        binding.statsSummaryPlays.text = formatPlays(stats.plays)

        fill(binding.statsSongsHeader, binding.statsSongs, stats.songs)
        fill(binding.statsArtistsHeader, binding.statsArtists, stats.artists)
        fill(binding.statsAlbumsHeader, binding.statsAlbums, stats.albums)
    }

    private fun fill(
        header: View,
        container: ViewGroup,
        items: List<RankedItem>,
    ) {
        header.isVisible = items.isNotEmpty()
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        items.forEachIndexed { index, item ->
            val row = ItemStatsRowBinding.inflate(inflater, container, false)
            row.statsRank.text = (index + 1).toString()
            row.statsName.text = item.name
            row.statsSubtitle.text = item.subtitle
            row.statsSubtitle.isVisible = !item.subtitle.isNullOrEmpty()
            row.statsCount.text = formatPlays(item.plays)
            container.addView(row.root)
        }
    }

    private fun formatPlays(plays: Int) =
        resources.getQuantityString(R.plurals.fmt_stats_plays, plays, plays)

    private fun formatListened(ms: Long): String {
        val totalMinutes = ms / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            getString(R.string.fmt_stats_time_hm, hours, minutes)
        } else {
            getString(R.string.fmt_stats_time_m, minutes)
        }
    }

    private fun formatRange(period: StatsPeriod, range: LongRange): String {
        val context = requireContext()
        return when (period) {
            StatsPeriod.WEEK ->
                DateUtils.formatDateRange(
                    context,
                    range.first,
                    range.last,
                    DateUtils.FORMAT_SHOW_DATE or
                        DateUtils.FORMAT_SHOW_YEAR or
                        DateUtils.FORMAT_ABBREV_MONTH,
                )
            StatsPeriod.MONTH ->
                DateUtils.formatDateRange(
                    context,
                    range.first,
                    range.last,
                    DateUtils.FORMAT_SHOW_DATE or
                        DateUtils.FORMAT_NO_MONTH_DAY or
                        DateUtils.FORMAT_SHOW_YEAR,
                )
            StatsPeriod.YEAR ->
                Calendar.getInstance()
                    .apply { timeInMillis = range.first }
                    .get(Calendar.YEAR)
                    .toString()
        }
    }
}
