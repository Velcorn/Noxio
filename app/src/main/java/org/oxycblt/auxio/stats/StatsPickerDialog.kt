/*
 * Copyright (c) 2026 Auxio Project
 * StatsPickerDialog.kt is part of Auxio.
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
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.oxycblt.auxio.databinding.DialogStatsPickerBinding
import org.oxycblt.auxio.ui.ViewBindingBottomSheetDialogFragment

@AndroidEntryPoint
class StatsPickerDialog : ViewBindingBottomSheetDialogFragment<DialogStatsPickerBinding>() {
    private val model: StatsViewModel by viewModels(ownerProducer = { requireParentFragment() })

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogStatsPickerBinding.inflate(inflater)

    override fun onBindingCreated(binding: DialogStatsPickerBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)
        val ui =
            model.ui.value
                ?: run {
                    dismiss()
                    return
                }
        binding.pickerTitle.text = getString(org.oxycblt.auxio.R.string.stats_pick_period)
        // A fixed height lets the list itself scroll, so it can open at the shown period.
        // Years are few and stay short.
        if (ui.query.period != StatsPeriod.YEAR) {
            binding.pickerWeeks.layoutParams.height =
                (resources.displayMetrics.heightPixels * 0.6f).toInt()
        }
        bindPeriods(binding, ui)
    }

    private fun bindPeriods(binding: DialogStatsPickerBinding, ui: StatsViewModel.Ui) {
        val period = ui.query.period
        val columns =
            when (period) {
                StatsPeriod.WEEK -> 7
                StatsPeriod.MONTH -> 4
                else -> 6
            }
        val adapter =
            PeriodAdapter(period, ui.range) { startMs ->
                model.goTo(firstOffset(period, startMs, System.currentTimeMillis()))
                dismiss()
            }
        val layoutManager = GridLayoutManager(requireContext(), columns)
        layoutManager.spanSizeLookup =
            object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) = adapter.spanOf(position, columns)
            }
        binding.pickerWeeks.layoutManager = layoutManager
        binding.pickerWeeks.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            adapter.submit(requireContext(), model.heatPeriods(period))
            binding.pickerWeeks.scrollToPosition(adapter.selectedPosition())
        }
    }

    /** Week numbers under an ISO year header, months under a calendar year, or years. */
    private class PeriodAdapter(
        private val period: StatsPeriod,
        private val range: LongRange,
        private val onClick: (Long) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private sealed interface Item {
            data class Header(val text: String) : Item

            data class Cell(val period: HeatPeriod, val sublabel: String) : Item

            /** Empty room in front of fewer than three years, so they sit in the middle. */
            data class Spacer(val span: Int) : Item
        }

        private var items = emptyList<Item>()

        fun isHeader(position: Int) = items[position] is Item.Header

        fun spanOf(position: Int, columns: Int) =
            when (val item = items[position]) {
                is Item.Header -> columns
                is Item.Spacer -> item.span
                is Item.Cell -> if (period == StatsPeriod.YEAR) 2 else 1
            }

        fun selectedPosition() =
            items.indexOfFirst { it is Item.Cell && it.period.startMs in range }.coerceAtLeast(0)

        fun submit(context: android.content.Context, periods: List<HeatPeriod>) {
            val result = mutableListOf<Item>()
            // Years take two of six columns; one or two years get room in front to be centered.
            if (period == StatsPeriod.YEAR && periods.size < 3) {
                result += Item.Spacer(3 - periods.size)
            }
            val calendar = Calendar.getInstance()
            var lastYear = -1
            for (heat in periods) {
                calendar.timeInMillis = heat.startMs
                val year = calendar.get(Calendar.YEAR)
                val labelYear =
                    if (period == StatsPeriod.WEEK) isoWeek(heat.startMs, calendar).second else year
                if (period != StatsPeriod.YEAR && labelYear != lastYear) {
                    result += Item.Header(labelYear.toString())
                    lastYear = labelYear
                }
                val sublabel =
                    if (period != StatsPeriod.WEEK && heat.totalMs > 0) {
                        StatsFormat.listened(context, heat.totalMs)
                    } else ""
                result += Item.Cell(heat, sublabel)
            }
            // Filled once, from empty.
            items = result
            notifyItemRangeInserted(0, result.size)
        }

        override fun getItemCount() = items.size

        override fun getItemViewType(position: Int) =
            when (items[position]) {
                is Item.Header -> 0
                is Item.Cell -> 1
                is Item.Spacer -> 2
            }

        override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder =
            if (type == 0) {
                object :
                    RecyclerView.ViewHolder(
                        TextView(parent.context).apply {
                            val appearance = TypedValue()
                            context.theme.resolveAttribute(
                                com.google.android.material.R.attr.textAppearanceLabelLarge,
                                appearance,
                                true,
                            )
                            setTextAppearance(appearance.resourceId)
                            setTextColor(
                                com.google.android.material.color.MaterialColors.getColor(
                                    this,
                                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                                ),
                            )
                            val density = resources.displayMetrics.density
                            setPadding(
                                (4 * density).toInt(),
                                (16 * density).toInt(),
                                0,
                                (4 * density).toInt(),
                            )
                        },
                    ) {}
            } else if (type == 2) {
                object : RecyclerView.ViewHolder(View(parent.context)) {}
            } else object : RecyclerView.ViewHolder(HeatCellView(parent.context)) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is Item.Header -> (holder.itemView as TextView).text = item.text
                is Item.Spacer -> Unit
                is Item.Cell -> {
                    val view = holder.itemView as HeatCellView
                    val heat = item.period
                    val label =
                        when (period) {
                            StatsPeriod.YEAR ->
                                SimpleDateFormat("yyyy", Locale.getDefault())
                                    .format(Date(heat.startMs))
                            StatsPeriod.MONTH ->
                                SimpleDateFormat("LLL", Locale.getDefault())
                                    .format(Date(heat.startMs))
                            StatsPeriod.WEEK ->
                                isoWeek(heat.startMs, Calendar.getInstance()).first.toString()
                            StatsPeriod.ALL -> ""
                        }
                    view.bind(
                        label,
                        item.sublabel,
                        heat.level,
                        heat.startMs in range,
                        compact = period == StatsPeriod.WEEK,
                    )
                    view.setOnClickListener { onClick(heat.startMs) }
                }
            }
        }
    }

    companion object {
        const val TAG = "StatsPickerDialog"
    }
}
