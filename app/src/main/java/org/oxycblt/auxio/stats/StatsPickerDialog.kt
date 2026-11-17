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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
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
        if (ui.query.period != StatsPeriod.YEAR) {
            binding.pickerWeeks.layoutParams.height =
                (resources.displayMetrics.heightPixels * 0.6f).toInt()
        }
        val days =
            listOf(
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY,
                Calendar.SATURDAY,
                Calendar.SUNDAY,
            )
        days.forEach { weekday ->
            val c = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, weekday) }
            binding.pickerWeekdays.addView(
                TextView(requireContext()).apply {
                    text = SimpleDateFormat("EEEEE", Locale.getDefault()).format(c.time)
                    gravity = android.view.Gravity.CENTER
                    layoutParams =
                        android.widget.LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f,
                        )
                },
            )
        }
        if (ui.query.period != StatsPeriod.WEEK) {
            bindPeriods(binding, ui)
            return
        }
        val adapter =
            WeekAdapter(ui.range) { day ->
                val offset = firstOffset(ui.query.period, day, System.currentTimeMillis())
                model.goTo(offset)
                dismiss()
            }
        val layoutManager = LinearLayoutManager(requireContext())
        binding.pickerWeeks.layoutManager = layoutManager
        binding.pickerWeeks.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            val weeks = model.heatmap()
            adapter.submit(weeks)
            val selectedDay =
                weeks
                    .asSequence()
                    .flatMap { it.days.asSequence() }
                    .filterNotNull()
                    .firstOrNull { it.dayMs in ui.range }
            if (selectedDay != null) {
                val position = adapter.positionForDay(selectedDay.dayMs)
                layoutManager.scrollToPositionWithOffset(position, 0)
            }
        }
    }

    private fun bindPeriods(binding: DialogStatsPickerBinding, ui: StatsViewModel.Ui) {
        binding.pickerWeekdays.visibility = View.GONE
        val period = ui.query.period
        val columns = if (period == StatsPeriod.YEAR) 3 else 4
        val adapter =
            PeriodAdapter(period, ui.range) { startMs ->
                model.goTo(firstOffset(period, startMs, System.currentTimeMillis()))
                dismiss()
            }
        val layoutManager = GridLayoutManager(requireContext(), columns)
        layoutManager.spanSizeLookup =
            object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) =
                    if (adapter.isHeader(position)) columns else 1
            }
        binding.pickerWeeks.layoutManager = layoutManager
        binding.pickerWeeks.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            adapter.submit(requireContext(), model.heatPeriods(period))
            binding.pickerWeeks.scrollToPosition(adapter.selectedPosition())
        }
    }

    /** Months under a header per year, or years, oldest first. */
    private class PeriodAdapter(
        private val period: StatsPeriod,
        private val range: LongRange,
        private val onClick: (Long) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private sealed interface Item {
            data class Header(val text: String) : Item

            data class Cell(val period: HeatPeriod, val sublabel: String) : Item
        }

        private var items = emptyList<Item>()

        fun isHeader(position: Int) = items[position] is Item.Header

        fun selectedPosition() =
            items.indexOfFirst { it is Item.Cell && it.period.startMs in range }.coerceAtLeast(0)

        fun submit(context: android.content.Context, periods: List<HeatPeriod>) {
            val result = mutableListOf<Item>()
            val calendar = Calendar.getInstance()
            var lastYear = -1
            for (heat in periods) {
                calendar.timeInMillis = heat.startMs
                val year = calendar.get(Calendar.YEAR)
                if (period == StatsPeriod.MONTH && year != lastYear) {
                    result += Item.Header(year.toString())
                    // Months before the first play keep their place in the year, empty.
                    repeat(calendar.get(Calendar.MONTH)) {
                        result += Item.Cell(HeatPeriod(-1, 0, 0), "")
                    }
                    lastYear = year
                }
                val sublabel =
                    if (heat.totalMs > 0) StatsFormat.listened(context, heat.totalMs) else ""
                result += Item.Cell(heat, sublabel)
            }
            // Filled once, from empty.
            items = result
            notifyItemRangeInserted(0, result.size)
        }

        override fun getItemCount() = items.size

        override fun getItemViewType(position: Int) = if (isHeader(position)) 0 else 1

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
            } else object : RecyclerView.ViewHolder(HeatCellView(parent.context)) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is Item.Header -> (holder.itemView as TextView).text = item.text
                is Item.Cell -> {
                    val view = holder.itemView as HeatCellView
                    val heat = item.period
                    if (heat.startMs < 0) {
                        view.visibility = View.INVISIBLE
                        view.setOnClickListener(null)
                        return
                    }
                    view.visibility = View.VISIBLE
                    val pattern = if (period == StatsPeriod.YEAR) "yyyy" else "LLL"
                    view.bind(
                        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(heat.startMs)),
                        item.sublabel,
                        heat.level,
                        heat.startMs in range,
                    )
                    view.setOnClickListener { onClick(heat.startMs) }
                }
            }
        }
    }

    private class WeekAdapter(private val range: LongRange, private val onClick: (Long) -> Unit) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private sealed interface Row {
            data class Header(val monthMs: Long) : Row

            data class Week(val week: HeatWeek) : Row
        }

        private var rows = emptyList<Row>()

        fun positionForDay(dayMs: Long): Int {
            val index = rows.indexOfFirst { row ->
                row is Row.Week && row.week.days.any { it?.dayMs == dayMs }
            }
            return if (
                index > 0 &&
                    rows[index - 1] is Row.Header &&
                    (rows[index - 1] as Row.Header).monthMs == monthStart(dayMs)
            ) {
                index - 1
            } else {
                index
            }
        }

        fun submit(items: List<HeatWeek>) {
            val result = mutableListOf<Row>()
            var previousMonth: Long? = null
            // Like a calendar: oldest at the top, each month from its 1st down to its last day.
            items.asReversed().forEach { week ->
                val months =
                    week.days.filterNotNull().map { monthStart(it.dayMs) }.distinct().sorted()
                months.forEach { month ->
                    if (month != previousMonth) result += Row.Header(month)
                    val days =
                        week.days.map { day -> day?.takeIf { monthStart(it.dayMs) == month } }
                    result += Row.Week(HeatWeek(week.startMs, days))
                    previousMonth = month
                }
            }
            // Filled once, from empty.
            rows = result
            notifyItemRangeInserted(0, result.size)
        }

        override fun getItemCount() = rows.size

        override fun getItemViewType(position: Int) =
            if (rows[position] is Row.Header) HEADER else WEEK

        override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder =
            if (type == HEADER) {
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
                            gravity = Gravity.START
                            val density = resources.displayMetrics.density
                            setPadding(0, (16 * density).toInt(), 0, (4 * density).toInt())
                            layoutParams =
                                RecyclerView.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                )
                        },
                    ) {}
            } else object : RecyclerView.ViewHolder(HeatmapWeekView(parent.context)) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Header -> {
                    val view = holder.itemView as TextView
                    val density = view.resources.displayMetrics.density
                    val gap = 4 * density
                    view.post {
                        val cell = (view.width - gap * 6) / 7f
                        val size = minOf(cell, 28 * density)
                        view.setPadding(
                            ((cell - size) / 2).toInt(),
                            (16 * density).toInt(),
                            0,
                            (4 * density).toInt(),
                        )
                    }
                    view.text =
                        SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(Date(row.monthMs))
                }
                is Row.Week -> bindWeek(holder, row.week)
            }
        }

        private fun bindWeek(holder: RecyclerView.ViewHolder, week: HeatWeek) {
            val view = holder.itemView as HeatmapWeekView
            view.week = week
            view.selectedRange = range
            view.onDayClick = onClick
            view.contentDescription =
                week.days.filterNotNull().joinToString {
                    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(it.dayMs))
                }
            view.setOnClickListener {}
        }

        private fun monthStart(dayMs: Long): Long =
            Calendar.getInstance().run {
                timeInMillis = dayMs
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                timeInMillis
            }

        companion object {
            private const val HEADER = 0
            private const val WEEK = 1
        }
    }

    companion object {
        const val TAG = "StatsPickerDialog"
    }
}
