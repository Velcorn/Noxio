/*
 * Copyright (c) 2026 Auxio Project
 * StatsViewModel.kt is part of Auxio.
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StatsViewModel @Inject constructor(private val repository: StatsRepository) : ViewModel() {
    /** Which period is shown. [offset] 0 is the current one, -1 the one before it. */
    data class Query(val period: StatsPeriod, val offset: Int)

    /** A [Query] together with its result, so the two never get out of sync on screen. */
    data class Ui(val query: Query, val range: LongRange, val stats: ListeningStats)

    private val mutableQuery = MutableStateFlow(Query(StatsPeriod.WEEK, 0))
    val query: StateFlow<Query> = mutableQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<Ui?> =
        mutableQuery
            .mapLatest {
                val range = statsRange(it.period, it.offset)
                Ui(it, range, repository.summary(range))
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun setPeriod(period: StatsPeriod) {
        if (period != mutableQuery.value.period) {
            mutableQuery.value = Query(period, 0)
        }
    }

    fun previous() {
        mutableQuery.value = mutableQuery.value.let { it.copy(offset = it.offset - 1) }
    }

    fun next() {
        mutableQuery.value =
            mutableQuery.value.let { it.copy(offset = (it.offset + 1).coerceAtMost(0)) }
    }
}
