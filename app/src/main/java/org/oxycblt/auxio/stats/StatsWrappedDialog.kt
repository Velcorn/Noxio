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
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogStatsWrappedBinding
import org.oxycblt.auxio.ui.ViewBindingBottomSheetDialogFragment
import org.oxycblt.auxio.util.systemBarInsetsCompat

/** The cards of a year or all-time review, each of which can be shared or saved. */
@AndroidEntryPoint
class StatsWrappedDialog : ViewBindingBottomSheetDialogFragment<DialogStatsWrappedBinding>() {
    private val statsModel: StatsViewModel by
        viewModels(ownerProducer = { requireParentFragment() })
    private var cards = listOf<Bitmap>()
    private var names = listOf<String>()
    private var wrapped: StatsViewModel.Wrapped? = null
    private var allTimeWrapped: StatsViewModel.AllTimeWrapped? = null
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
        viewLifecycleOwner.lifecycleScope.launch {
            val isAllTime = requireArguments().getBoolean(ARG_ALL_TIME)
            val year = requireArguments().getInt(ARG_YEAR)
            val empty =
                if (isAllTime) {
                    statsModel.loadAllTimeWrapped().also { allTimeWrapped = it }.review.isEmpty
                } else {
                    statsModel.loadWrapped(year).also { wrapped = it }.review.isEmpty
                }
            if (empty) {
                android.widget.Toast.makeText(
                        requireContext(),
                        getString(
                            R.string.fmt_stats_wrapped_empty,
                            if (isAllTime) getString(R.string.lbl_stats_all_time)
                            else year.toString(),
                        ),
                        android.widget.Toast.LENGTH_SHORT,
                    )
                    .show()
                dismiss()
                return@launch
            }
            redraw(binding)
        }
        binding.shareSend.setOnClickListener {
            val page = binding.wrappedPager.currentItem
            cards.getOrNull(page)?.let { shareStatsImage(requireContext(), it, names[page]) }
        }
        binding.shareSave.setOnClickListener {
            names.getOrNull(binding.wrappedPager.currentItem)?.let {
                saveLauncher.launch(statsImageFileName(it))
            }
        }
        binding.shareName.setOnClickListener { editName(binding) }
    }

    private fun redraw(binding: DialogStatsWrappedBinding) {
        val name =
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(getString(R.string.set_key_wrapped_name), "")
                .orEmpty()
                .trim()
        val built =
            when {
                allTimeWrapped != null -> allTimeCards(requireContext(), allTimeWrapped!!, name)
                wrapped != null -> wrappedCards(requireContext(), wrapped!!, name)
                else -> return
            }
        cards = built.map { StatsImage.renderItem(requireContext(), it.second) }
        names = built.map { it.first }
        show(binding)
    }

    private fun editName(binding: DialogStatsWrappedBinding) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val field =
            EditText(requireContext()).apply {
                setSingleLine(true)
                setText(prefs.getString(getString(R.string.set_key_wrapped_name), ""))
                setSelection(text.length)
            }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lbl_stats_wrapped_name)
            .setView(field)
            .setNegativeButton(R.string.lbl_cancel, null)
            .setPositiveButton(R.string.lbl_ok) { _, _ ->
                prefs.edit {
                    putString(
                        getString(R.string.set_key_wrapped_name),
                        field.text.toString().trim(),
                    )
                }
                redraw(binding)
            }
            .show()
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
            },
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
                },
            )

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.image.setImageBitmap(cards[position])
        }

        override fun getItemCount() = cards.size
    }

    companion object {
        const val TAG = "StatsWrappedDialog"
        private const val ARG_YEAR = "year"
        private const val ARG_ALL_TIME = "all_time"

        fun of(year: Int) = StatsWrappedDialog().apply { arguments = bundleOf(ARG_YEAR to year) }

        fun allTime() = StatsWrappedDialog().apply { arguments = bundleOf(ARG_ALL_TIME to true) }
    }
}

/** The cards of a year review, each with the file name it is saved under. */
internal fun wrappedCards(
    context: Context,
    wrapped: StatsViewModel.Wrapped,
    name: String = "",
    locale: Locale = Locale.getDefault(),
): List<Pair<String, StatsItemImageContent>> {
    val review = wrapped.review
    val year = review.year.toString()
    val title = if (name.isBlank()) year else possessive(name.trim(), review.year, locale)
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
        "$year" to
            StatsItemImageContent(
                category = StatsCategory.ARTISTS,
                name = title,
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
                sectionTitle = context.getString(R.string.lbl_stats_wrapped_chart_title),
                rows = emptyList(),
                bars =
                    if (review.monthListenedMs.maxOrNull() == 0L) emptyList()
                    else
                        review.monthListenedMs.mapIndexed { index, ms ->
                            StatsItemImageContent.Bar(
                                if (review.monthListenedMs.maxOrNull() == 0L) 0f
                                else ms.toFloat() / (review.monthListenedMs.maxOrNull() ?: 1L),
                                listeningHoursLabel(ms),
                                monthLabels[index],
                            )
                        },
                footerStart = busiestMonth,
                footerEnd = busiestDay,
            )
    wrapped.topArtist?.let {
        cards +=
            NoxioFileName.label(it.name) to
                itemImageContent(context, 1, StatsPeriod.YEAR, review.range, it)
    }
    if (review.topSongs.isNotEmpty()) {
        cards +=
            "$year-Songs" to
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
    timeOfDay(review.hourPlays)?.let { time ->
        cards +=
            "$year-Clock" to
                clockCard(context, title, review.hourPlays, time, review.busiestWeekday)
    }
    cards +=
        "$year-Summary" to
            StatsItemImageContent(
                category = StatsCategory.ALBUMS,
                name = title,
                // No subtitle: the heatmap of the year takes its place.
                lines = emptyList(),
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
                heatLevels = review.dailyLevels,
                heatYear = review.year,
            )
    return cards
}

internal fun allTimeCards(
    context: Context,
    wrapped: StatsViewModel.AllTimeWrapped,
    name: String = "",
    locale: Locale = Locale.getDefault(),
): List<Pair<String, StatsItemImageContent>> {
    val review = wrapped.review
    val title =
        if (name.isBlank()) context.getString(R.string.lbl_stats_all_time)
        else context.getString(R.string.fmt_stats_wrapped_named_all_time, name.trim())
    val weekdays = DateFormatSymbols.getInstance(locale).weekdays
    val busiestDay =
        review.busiestWeekday?.let {
            context.getString(R.string.fmt_stats_wrapped_busiest_day, weekdays[it])
        } ?: ""
    val listened = StatsFormat.listened(context, review.listenedMs)
    val since =
        review.firstPlayAt?.let {
            context.getString(R.string.fmt_stats_wrapped_since, StatsFormat.date(context, it))
        } ?: ""
    val chartYears = review.years.takeLast(8)
    val maxChartTime = chartYears.maxOfOrNull { it.listenedMs } ?: 0L
    val cards = mutableListOf<Pair<String, StatsItemImageContent>>()
    cards +=
        "All-time" to
            StatsItemImageContent(
                category = StatsCategory.ARTISTS,
                name = title,
                lines = listOfNotNull(since.takeIf { it.isNotEmpty() }),
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
                sectionTitle = context.getString(R.string.lbl_stats_wrapped_year_chart_title),
                rows = emptyList(),
                bars =
                    chartYears.map { year ->
                        StatsItemImageContent.Bar(
                            if (maxChartTime == 0L) 0f
                            else year.listenedMs.toFloat() / maxChartTime,
                            listeningHoursLabel(year.listenedMs),
                            year.year.toString(),
                        )
                    },
                footerStart =
                    review.busiestYear?.let {
                        context.getString(R.string.fmt_stats_wrapped_busiest_year, it.toString())
                    } ?: "",
                footerEnd = busiestDay,
            )
    val recent = review.years.takeLast(5).asReversed()
    if (recent.isNotEmpty()) {
        cards +=
            "All-time-Years" to
                StatsItemImageContent(
                    category = StatsCategory.ARTISTS,
                    name = context.getString(R.string.lbl_stats_wrapped_through_years),
                    lines = emptyList(),
                    tiles = emptyList(),
                    sectionTitle = "",
                    rows =
                        recent.map { year ->
                            val subtitle =
                                listOfNotNull(year.topArtist?.name, year.topSong?.name)
                                    .joinToString(" · ")
                                    .ifEmpty { null }
                            StatsImageContent.Row(
                                year.year.toString(),
                                subtitle,
                                StatsFormat.plays(context, year.plays),
                                StatsFormat.listened(context, year.listenedMs),
                            )
                        },
                    bars = emptyList(),
                    footerStart = "",
                    footerEnd = "",
                    rankedRows = false,
                )
    }
    wrapped.topArtist?.let {
        cards +=
            NoxioFileName.label(it.name) to
                itemImageContent(context, 1, StatsPeriod.ALL, review.range, it)
    }
    if (review.topSongs.isNotEmpty()) {
        cards +=
            "All-time-Songs" to
                StatsItemImageContent(
                    category = StatsCategory.SONGS,
                    name = context.getString(R.string.lbl_stats_top_songs),
                    lines = listOf(context.getString(R.string.lbl_stats_all_time)),
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
    timeOfDay(review.hourPlays)?.let { time ->
        cards +=
            "All-time-Clock" to
                clockCard(context, title, review.hourPlays, time, review.busiestWeekday, locale)
    }
    cards +=
        "All-time-Summary" to
            StatsItemImageContent(
                category = StatsCategory.ALBUMS,
                name = title,
                lines = emptyList(),
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
                footerStart = "",
                footerEnd = "",
                yearGrid = review.yearMonthLevels.takeLast(6),
            )
    return cards
}

private fun clockCard(
    context: Context,
    title: String,
    hours: List<Int>,
    time: TimeOfDay,
    busiestWeekday: Int?,
    locale: Locale = Locale.getDefault(),
): StatsItemImageContent {
    val label =
        context.getString(
            when (time) {
                TimeOfDay.MORNING -> R.string.lbl_stats_clock_morning
                TimeOfDay.AFTERNOON -> R.string.lbl_stats_clock_afternoon
                TimeOfDay.EVENING -> R.string.lbl_stats_clock_evening
                TimeOfDay.NIGHT -> R.string.lbl_stats_clock_night
            },
        )
    val weekdays = DateFormatSymbols.getInstance(locale).weekdays
    return StatsItemImageContent(
        category = StatsCategory.ARTISTS,
        name = title,
        lines = listOf(context.getString(R.string.lbl_stats_wrapped_when_listening)),
        tiles = emptyList(),
        sectionTitle = "",
        rows = emptyList(),
        bars = emptyList(),
        footerStart = label,
        footerEnd =
            busiestWeekday?.let {
                context.getString(R.string.fmt_stats_wrapped_busiest_day, weekdays[it])
            } ?: "",
        clockHours = hours,
    )
}
