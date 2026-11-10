/*
 * Copyright (c) 2026 Auxio Project
 * StatsWrappedDialog.kt is part of Auxio.
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
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import dagger.hilt.android.AndroidEntryPoint
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogStatsWrappedBinding
import org.oxycblt.auxio.ui.ViewBindingBottomSheetDialogFragment
import org.oxycblt.auxio.util.systemBarInsetsCompat

/**
 * The Wrapped cards of a year: the year as a whole, its top artist, top songs and top album, and a
 * summary. Swiped through, each can be shared or saved like the other cards.
 */
@AndroidEntryPoint
class StatsWrappedDialog : ViewBindingBottomSheetDialogFragment<DialogStatsWrappedBinding>() {
    private val statsModel: StatsViewModel by
        viewModels(ownerProducer = { requireParentFragment() })
    private var cards = listOf<Bitmap>()
    private var names = listOf<String>()
    private val saveLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
            val image = cards.getOrNull(requireBinding().wrappedPager.currentItem)
            if (uri != null && image != null) {
                saveStatsImage(requireContext(), image, uri)
            }
        }

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogStatsWrappedBinding.inflate(inflater)

    override fun onBindingCreated(binding: DialogStatsWrappedBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)
        binding.root.setOnApplyWindowInsetsListener { view, insets ->
            view.updatePadding(bottom = insets.systemBarInsetsCompat.bottom)
            insets
        }
        val year = requireArguments().getInt(ARG_YEAR)
        viewLifecycleOwner.lifecycleScope.launch {
            val wrapped = statsModel.loadWrapped(year)
            if (wrapped.review.isEmpty) {
                android.widget.Toast.makeText(
                        requireContext(),
                        getString(R.string.fmt_stats_wrapped_empty, year.toString()),
                        android.widget.Toast.LENGTH_SHORT,
                    )
                    .show()
                dismiss()
                return@launch
            }
            val built = wrappedCards(requireContext(), wrapped)
            cards = built.map { StatsImage.renderItem(requireContext(), it.second) }
            names = built.map { it.first }
            show(binding)
        }
        binding.shareSend.setOnClickListener {
            cards.getOrNull(binding.wrappedPager.currentItem)?.let {
                shareStatsImage(requireContext(), it)
            }
        }
        binding.shareSave.setOnClickListener {
            names.getOrNull(binding.wrappedPager.currentItem)?.let { saveLauncher.launch(it) }
        }
    }

    private fun show(binding: DialogStatsWrappedBinding) {
        binding.wrappedPager.adapter = CardAdapter(cards)
        fun page(index: Int) {
            binding.wrappedPage.text =
                getString(R.string.fmt_stats_wrapped_page, index + 1, cards.size)
        }
        page(0)
        binding.wrappedPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = page(position)
            }
        )
    }

    override fun onDestroyBinding(binding: DialogStatsWrappedBinding) {
        binding.wrappedPager.adapter = null
        cards = emptyList()
    }

    private class CardAdapter(private val cards: List<Bitmap>) :
        RecyclerView.Adapter<CardAdapter.Holder>() {
        class Holder(val image: ImageView) : RecyclerView.ViewHolder(image)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(
                ImageView(parent.context).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = parent.context.getString(R.string.lbl_stats_share_preview)
                }
            )

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.image.setImageBitmap(cards[position])
        }

        override fun getItemCount() = cards.size
    }

    companion object {
        const val TAG = "StatsWrappedDialog"
        private const val ARG_YEAR = "year"

        fun of(year: Int) = StatsWrappedDialog().apply { arguments = bundleOf(ARG_YEAR to year) }
    }
}

/**
 * The cards of a year in review, each with the file name it is saved under: the year as a whole,
 * the top artist, the top songs, the top album and a summary of all of it.
 */
internal fun wrappedCards(
    context: Context,
    wrapped: StatsViewModel.Wrapped,
    locale: Locale = Locale.getDefault(),
): List<Pair<String, StatsItemImageContent>> {
    val review = wrapped.review
    val year = review.year.toString()
    val months = DateFormatSymbols.getInstance(locale).months
    val weekdays = DateFormatSymbols.getInstance(locale).weekdays
    val busiestMonth =
        review.busiestMonth?.let {
            context.getString(R.string.fmt_stats_wrapped_busiest_month, months[it])
        } ?: ""
    val busiestDay =
        review.busiestWeekday?.let {
            context.getString(R.string.fmt_stats_wrapped_busiest_day, weekdays[it])
        } ?: ""
    val change =
        review.change?.let {
            context.getString(
                if (it >= 0) R.string.fmt_stats_wrapped_more else R.string.fmt_stats_wrapped_less,
                StatsFormat.percent(kotlin.math.abs(it), locale),
                (review.year - 1).toString(),
            )
        }
    val mostMonthPlays = review.monthPlays.maxOrNull() ?: 0
    val calendar = Calendar.getInstance()
    val monthStarts =
        (0 until 12).map {
            calendar.clear()
            calendar.set(review.year, it, 1)
            calendar.timeInMillis
        }
    val monthLabels =
        StatsFormat.bucketLabels(StatsPeriod.YEAR, ItemStats.BucketUnit.MONTH, monthStarts, locale)
    val listened = StatsFormat.listened(context, review.listenedMs)

    val cards = mutableListOf<Pair<String, StatsItemImageContent>>()
    cards +=
        "noxio-$year.png" to
            StatsItemImageContent(
                category = StatsCategory.ARTISTS,
                name = context.getString(R.string.fmt_stats_wrapped_title, year),
                lines =
                    listOfNotNull(context.getString(R.string.lbl_stats_wrapped_subtitle), change),
                tiles =
                    listOf(
                        StatsItemImageContent.Tile(
                            listened,
                            context.getString(R.string.lbl_stats_item_time),
                        ),
                        StatsItemImageContent.Tile(
                            review.plays.toString(),
                            context.getString(R.string.lbl_stats_item_plays),
                        ),
                        StatsItemImageContent.Tile(
                            review.songs.toString(),
                            context.getString(R.string.lbl_stats_wrapped_songs),
                        ),
                        StatsItemImageContent.Tile(
                            review.artists.toString(),
                            context.getString(R.string.lbl_stats_wrapped_artists),
                        ),
                    ),
                sectionTitle = context.getString(R.string.lbl_stats_item_over_time),
                rows = emptyList(),
                bars =
                    if (mostMonthPlays == 0) emptyList()
                    else
                        review.monthPlays.mapIndexed { index, plays ->
                            StatsItemImageContent.Bar(
                                plays.toFloat() / mostMonthPlays,
                                monthLabels[index],
                            )
                        },
                footerStart = busiestMonth,
                footerEnd = busiestDay,
            )
    wrapped.topArtist?.let {
        cards +=
            statsImageFileName(it.name) to
                itemImageContent(context, 1, StatsPeriod.YEAR, review.range, it)
    }
    if (review.topSongs.isNotEmpty()) {
        cards +=
            "noxio-$year-songs.png" to
                StatsItemImageContent(
                    category = StatsCategory.SONGS,
                    name = context.getString(R.string.lbl_stats_top_songs),
                    lines = listOf(year),
                    tiles = emptyList(),
                    sectionTitle = "",
                    rows =
                        review.topSongs.map {
                            StatsImageContent.Row(
                                it.name,
                                it.subtitle,
                                StatsFormat.plays(context, it.plays),
                                StatsFormat.listened(context, it.listenedMs),
                            )
                        },
                    bars = emptyList(),
                    footerStart = listened,
                    footerEnd = StatsFormat.plays(context, review.plays),
                )
    }
    wrapped.topAlbum?.let {
        cards +=
            statsImageFileName(it.name) to
                itemImageContent(context, 1, StatsPeriod.YEAR, review.range, it)
    }
    cards +=
        "noxio-$year-summary.png" to
            StatsItemImageContent(
                category = StatsCategory.ALBUMS,
                name = context.getString(R.string.fmt_stats_wrapped_summary, year),
                lines = listOfNotNull(change),
                tiles =
                    listOfNotNull(
                        StatsItemImageContent.Tile(
                            listened,
                            context.getString(R.string.lbl_stats_item_time),
                        ),
                        StatsItemImageContent.Tile(
                            review.plays.toString(),
                            context.getString(R.string.lbl_stats_item_plays),
                        ),
                        review.topArtist?.let {
                            StatsItemImageContent.Tile(
                                it.name,
                                context.getString(R.string.lbl_stats_wrapped_top_artist),
                            )
                        },
                        review.topAlbum?.let {
                            StatsItemImageContent.Tile(
                                it.name,
                                context.getString(R.string.lbl_stats_wrapped_top_album),
                            )
                        },
                    ),
                sectionTitle = context.getString(R.string.lbl_stats_top_songs),
                rows =
                    review.topSongs.take(3).map {
                        StatsImageContent.Row(
                            it.name,
                            null,
                            StatsFormat.plays(context, it.plays),
                            StatsFormat.listened(context, it.listenedMs),
                        )
                    },
                bars = emptyList(),
                footerStart = busiestMonth,
                footerEnd = busiestDay,
            )
    return cards
}
