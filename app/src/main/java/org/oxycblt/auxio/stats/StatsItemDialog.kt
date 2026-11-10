/*
 * Copyright (c) 2026 Auxio Project
 * StatsItemDialog.kt is part of Auxio.
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
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogStatsShareBinding
import org.oxycblt.auxio.ui.ViewBindingBottomSheetDialogFragment
import org.oxycblt.auxio.util.getDimenPixels
import org.oxycblt.auxio.util.systemBarInsetsCompat

/**
 * A closer look at one artist, album or song of the stats screen. It is shown as the image that can
 * be shared or saved from here, like the card in [StatsShareDialog]. The numbers are loaded by the
 * [StatsViewModel] of the stats screen before the dialog is opened.
 */
@AndroidEntryPoint
class StatsItemDialog : ViewBindingBottomSheetDialogFragment<DialogStatsShareBinding>() {
    private val statsModel: StatsViewModel by
        viewModels(ownerProducer = { requireParentFragment() })
    private var bitmap: Bitmap? = null
    private val saveLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
            val image = bitmap
            if (uri != null && image != null) {
                saveStatsImage(requireContext(), image, uri)
            }
        }

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogStatsShareBinding.inflate(inflater)

    override fun onBindingCreated(binding: DialogStatsShareBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.root.setOnApplyWindowInsetsListener { view, insets ->
            view.updatePadding(bottom = insets.systemBarInsetsCompat.bottom)
            insets
        }

        val details = statsModel.details
        if (details == null) {
            dismiss()
            return
        }
        val content =
            itemImageContent(
                requireContext(),
                details.rank,
                details.query.period,
                details.range,
                details.stats,
            )
        val image = StatsImage.renderItem(requireContext(), content)
        bitmap = image

        // The image is what is read here, so it is as wide as the sheet instead of a small preview.
        binding.sharePreview.adjustViewBounds = true
        binding.sharePreview.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            marginStart = requireContext().getDimenPixels(R.dimen.spacing_medium)
            marginEnd = marginStart
        }
        binding.sharePreview.setImageBitmap(image)
        binding.sharePreview.contentDescription = describe(content)

        binding.shareSend.setOnClickListener {
            bitmap?.let { shareStatsImage(requireContext(), it) }
        }
        binding.shareSave.setOnClickListener {
            saveLauncher.launch(statsImageFileName(details.stats.name))
        }
    }

    override fun onDestroyBinding(binding: DialogStatsShareBinding) {
        binding.sharePreview.setImageDrawable(null)
        bitmap = null
    }

    /** The text of the image, for a screen reader. */
    private fun describe(content: StatsItemImageContent) =
        (listOf(content.name) +
                content.lines +
                content.tiles.map { "${it.label}: ${it.value}" } +
                content.rows.map { "${it.name}: ${it.plays}" } +
                listOf(content.footerStart, content.footerEnd))
            .filter { it.isNotEmpty() }
            .joinToString(". ")

    companion object {
        const val TAG = "StatsItemDialog"
    }
}

/** The card of a single artist, album or song, at its [rank] in the [period] of [range]. */
internal fun itemImageContent(
    context: Context,
    rank: Int,
    period: StatsPeriod,
    range: LongRange,
    stats: ItemStats,
): StatsItemImageContent {
    val rangeLabel = StatsFormat.range(context, period, range)
    val rankLine =
        context.getString(
            when (stats.category) {
                StatsCategory.ARTISTS -> R.string.fmt_stats_item_artist
                StatsCategory.ALBUMS -> R.string.fmt_stats_item_album
                StatsCategory.SONGS -> R.string.fmt_stats_item_song
            },
            rank,
            rangeLabel,
        )
    val labels =
        StatsFormat.bucketLabels(period, stats.bucketUnit, stats.buckets.map { it.startMs })
    val mostPlays = stats.buckets.maxOfOrNull { it.plays } ?: 0
    return StatsItemImageContent(
        category = stats.category,
        name = stats.name,
        lines =
            listOfNotNull(
                stats.subtitle?.ifEmpty { null },
                rankLine,
                if (stats.estimatedPlays > 0) {
                    context.resources.getQuantityString(
                        R.plurals.fmt_stats_item_estimated,
                        stats.estimatedPlays,
                        stats.estimatedPlays,
                    )
                } else {
                    null
                },
            ),
        tiles =
            listOf(
                StatsItemImageContent.Tile(
                    stats.plays.toString(),
                    context.getString(R.string.lbl_stats_item_plays),
                ),
                StatsItemImageContent.Tile(
                    StatsFormat.listened(context, stats.listenedMs),
                    context.getString(R.string.lbl_stats_item_time),
                ),
                StatsItemImageContent.Tile(
                    StatsFormat.percent(stats.share),
                    context.getString(R.string.lbl_stats_item_share),
                ),
                if (stats.countsWeeks) {
                    StatsItemImageContent.Tile(
                        stats.weeksPlayed.toString(),
                        context.getString(R.string.lbl_stats_item_weeks),
                    )
                } else {
                    StatsItemImageContent.Tile(
                        // Imported plays have no real day.
                        if (stats.daysPlayed == 0 && stats.estimatedPlays > 0) "–"
                        else stats.daysPlayed.toString(),
                        context.getString(R.string.lbl_stats_item_days),
                    )
                },
            ),
        sectionTitle =
            context.getString(
                if (stats.topSongs.isEmpty()) R.string.lbl_stats_item_over_time
                else R.string.lbl_stats_top_songs
            ),
        rows =
            stats.topSongs.map {
                StatsImageContent.Row(
                    it.name,
                    // The artist is in the heading already.
                    null,
                    StatsFormat.plays(context, it.plays),
                    StatsFormat.listened(context, it.listenedMs),
                )
            },
        bars =
            if (mostPlays == 0) {
                emptyList()
            } else {
                stats.buckets.mapIndexed { index, bucket ->
                    StatsItemImageContent.Bar(bucket.plays.toFloat() / mostPlays, labels[index])
                }
            },
        footerStart =
            stats.firstPlayedMs?.let {
                context.getString(
                    if (stats.firstPlayEstimated) R.string.fmt_stats_item_first_estimated
                    else R.string.fmt_stats_item_first,
                    StatsFormat.date(context, it),
                )
            } ?: "",
        // Inside a period the total of all time is the other number of interest. All time has
        // it at the top already.
        footerEnd =
            if (period != StatsPeriod.ALL) {
                context.getString(
                    R.string.fmt_stats_item_total,
                    StatsFormat.plays(context, stats.allTimePlays),
                )
            } else {
                stats.lastPlayedMs?.let {
                    context.getString(
                        if (stats.lastPlayEstimated) R.string.fmt_stats_item_last_estimated
                        else R.string.fmt_stats_item_last,
                        StatsFormat.date(context, it),
                    )
                } ?: ""
            },
    )
}
