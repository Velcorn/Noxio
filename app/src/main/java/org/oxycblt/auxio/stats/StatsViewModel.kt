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
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** What the ranked list on the stats screen is about. */
enum class StatsCategory {
    ARTISTS,
    ALBUMS,
    SONGS,
}

@HiltViewModel
class StatsViewModel @Inject constructor(private val repository: StatsRepository) : ViewModel() {
    /** Which period is shown. [offset] 0 is the current one, -1 the one before it. */
    data class Query(val period: StatsPeriod, val offset: Int)

    /** A [Query] together with its result, so the two never get out of sync on screen. */
    data class Ui(val query: Query, val range: LongRange, val stats: ListeningStats)

    private val mutableQuery = MutableStateFlow(Query(StatsPeriod.WEEK, 0))
    val query: StateFlow<Query> = mutableQuery.asStateFlow()

    private val mutableCategory = MutableStateFlow(StatsCategory.ARTISTS)
    val category: StateFlow<StatsCategory> = mutableCategory.asStateFlow()

    /** Whether the card shows the top 10 instead of the top 5. */
    private val mutableExpanded = MutableStateFlow(false)
    val expanded: StateFlow<Boolean> = mutableExpanded.asStateFlow()

    /** Bumped when the history changed outside of the player, for example after an import. */
    private val refresh = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<Ui?> =
        combine(mutableQuery, refresh) { query, _ -> query }
            .mapLatest {
                val range = statsRange(it.period, it.offset)
                Ui(it, range, repository.summary(range))
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun setPeriod(period: StatsPeriod) {
        if (period != mutableQuery.value.period) {
            mutableQuery.value = Query(period, 0)
            mutableExpanded.value = false
        }
    }

    fun previous() {
        mutableQuery.value = mutableQuery.value.let { it.copy(offset = it.offset - 1) }
    }

    fun next() {
        mutableQuery.value =
            mutableQuery.value.let { it.copy(offset = (it.offset + 1).coerceAtMost(0)) }
    }

    fun setCategory(category: StatsCategory) {
        if (category != mutableCategory.value) {
            mutableCategory.value = category
            mutableExpanded.value = false
        }
    }

    fun toggleExpanded() {
        mutableExpanded.value = !mutableExpanded.value
    }

    /** The ranked rows of the current category in [ui]. */
    fun items(ui: Ui): List<RankedItem> =
        when (category.value) {
            StatsCategory.ARTISTS -> ui.stats.artists
            StatsCategory.ALBUMS -> ui.stats.albums
            StatsCategory.SONGS -> ui.stats.songs
        }

    /** Write the full play history as CSV. Must be called off the main thread. */
    suspend fun exportCsv(out: OutputStream) = repository.exportCsv(out)

    /** Add the plays of imported files. Must be called off the main thread. */
    suspend fun importCsv(files: List<StatsCsvImport.Parsed>): Int {
        val added = repository.importCsv(files)
        refresh.value += 1
        return added
    }
}
