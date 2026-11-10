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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.R

/** What the ranked list on the stats screen is about. */
enum class StatsCategory {
    ARTISTS,
    ALBUMS,
    SONGS,
}

/**
 * The category next to [current] in the order artists, albums, songs, for swiping.
 *
 * @param forward Whether to go towards songs.
 * @return The neighbour, or null at the end of the row.
 */
fun neighbourCategory(current: StatsCategory, forward: Boolean): StatsCategory? {
    val all = StatsCategory.entries
    return all.getOrNull(all.indexOf(current) + if (forward) 1 else -1)
}

@HiltViewModel
class StatsViewModel
@Inject
constructor(
    private val repository: StatsRepository,
    private val settings: StatsSettings,
    @ApplicationContext private val context: Context,
) : ViewModel() {
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

    /** A closer look at one row of the card, with the period it was shown for. */
    data class Details(
        /** The place of the row in the list, 1 for the first. */
        val rank: Int,
        val query: Query,
        val range: LongRange,
        val stats: ItemStats,
    )

    /** What [loadDetails] found last, for the dialog that shows it. */
    var details: Details? = null
        private set

    /** Bumped when the history changed outside of the player, for example after an import. */
    private val refresh = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<Ui?> =
        combine(mutableQuery, refresh) { query, _ -> query }
            .mapLatest {
                val range =
                    statsRange(it.period, it.offset, firstDayOfWeek = settings.firstDayOfWeek)
                Ui(it, range, repository.summary(range, context.getString(R.string.def_artist)))
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

    /**
     * Take a closer look at the row [item] of the current category and keep it in [details].
     *
     * @param rank Its place in the list, 1 for the first.
     * @return Whether there is something to show.
     */
    suspend fun loadDetails(rank: Int, item: RankedItem): Boolean {
        val ui = ui.value ?: return false
        val category = category.value
        val stats =
            withContext(Dispatchers.IO) {
                repository.itemStats(
                    category,
                    item.key,
                    ui.query.period,
                    ui.range,
                    context.getString(R.string.def_artist),
                )
            } ?: return false
        details = Details(rank, ui.query, ui.range, stats)
        return true
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
