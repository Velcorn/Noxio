/*
 * Copyright (c) 2026 Auxio Project
 * StatsFormat.kt is part of Auxio.
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
import android.text.format.DateUtils
import java.util.Calendar
import org.oxycblt.auxio.R

/** Text formatting shared by the stats screen and the image that can be shared from it. */
object StatsFormat {
    fun plays(context: Context, plays: Int): String =
        context.resources.getQuantityString(R.plurals.fmt_stats_plays, plays, plays)

    /** A listening time such as "2 h 5 min", or "<1 min". */
    fun listened(context: Context, ms: Long): String {
        val totalMinutes = ms / 60_000
        if (totalMinutes == 0L) {
            return context.getString(R.string.lbl_stats_under_minute)
        }
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            context.getString(R.string.fmt_stats_time_hm, hours, minutes)
        } else {
            context.getString(R.string.fmt_stats_time_m, minutes)
        }
    }

    /** The label of a period, for example "Nov 2 - Nov 8, 2026" or "2026". */
    fun range(context: Context, period: StatsPeriod, range: LongRange): String =
        when (period) {
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
            StatsPeriod.ALL -> context.getString(R.string.lbl_stats_all)
            StatsPeriod.YEAR ->
                Calendar.getInstance()
                    .apply { timeInMillis = range.first }
                    .get(Calendar.YEAR)
                    .toString()
        }
}
