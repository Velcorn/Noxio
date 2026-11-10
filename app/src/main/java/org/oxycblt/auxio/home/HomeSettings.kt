/*
 * Copyright (c) 2023 Auxio Project
 * HomeSettings.kt is part of Auxio.
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
 
package org.oxycblt.auxio.home

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.home.tabs.Tab
import org.oxycblt.auxio.music.MusicType
import org.oxycblt.auxio.settings.Settings
import org.oxycblt.auxio.util.unlikelyToBeNull
import timber.log.Timber as L

/**
 * User configuration specific to the home UI.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
interface HomeSettings : Settings<HomeSettings.Listener> {
    /** The tabs to show in the home UI. */
    var homeTabs: Array<Tab>
    /** Whether to hide artists considered "collaborators" from the home UI. */
    val shouldHideCollaborators: Boolean

    interface Listener {
        /** Called when the [homeTabs] configuration changes. */
        fun onTabsChanged() {}

        /** Called when the [shouldHideCollaborators] configuration changes. */
        fun onHideCollaboratorsChanged() {}
    }
}

class HomeSettingsImpl @Inject constructor(@ApplicationContext context: Context) :
    Settings.Impl<HomeSettings.Listener>(context), HomeSettings {
    override var homeTabs: Array<Tab>
        get() =
            Tab.fromIntCode(
                sharedPreferences.getInt(
                    getString(R.string.set_key_home_tabs),
                    Tab.SEQUENCE_DEFAULT,
                )
            ) ?: unlikelyToBeNull(Tab.fromIntCode(Tab.SEQUENCE_DEFAULT))
        set(value) {
            sharedPreferences.edit {
                putInt(getString(R.string.set_key_home_tabs), Tab.toIntCode(value))
                apply()
            }
        }

    override val shouldHideCollaborators: Boolean
        get() = sharedPreferences.getBoolean(getString(R.string.set_key_hide_collaborators), false)

    override fun migrate() {
        resetOldDefaultTabs()
        if (sharedPreferences.contains(OLD_KEY_LIB_TABS)) {
            L.d("Migrating tab setting")
            val oldTabs =
                Tab.fromIntCode(sharedPreferences.getInt(OLD_KEY_LIB_TABS, Tab.SEQUENCE_DEFAULT))
                    ?: unlikelyToBeNull(Tab.fromIntCode(Tab.SEQUENCE_DEFAULT))

            // The playlist tab is now parsed, but it needs to be made visible.
            val playlistIndex = oldTabs.indexOfFirst { it.type == MusicType.PLAYLISTS }
            check(playlistIndex > -1) // This should exist, otherwise we are in big trouble
            oldTabs[playlistIndex] = Tab.Visible(MusicType.PLAYLISTS)

            sharedPreferences.edit {
                putInt(getString(R.string.set_key_home_tabs), Tab.toIntCode(oldTabs))
                remove(OLD_KEY_LIB_TABS)
            }
        }
    }

    /**
     * Noxio's default order is Artists, Albums, Songs, Playlists. An order saved before that, which
     * is still Auxio's default (Songs, Albums, Artists, Playlists, with or without a Genres tab),
     * is replaced once; an order that was set by hand is kept.
     */
    private fun resetOldDefaultTabs() {
        val resetKey = getString(R.string.set_key_home_tabs_reset)
        if (sharedPreferences.getBoolean(resetKey, false)) return
        val tabsKey = getString(R.string.set_key_home_tabs)
        sharedPreferences.edit {
            if (sharedPreferences.contains(tabsKey)) {
                val saved = Tab.fromIntCode(sharedPreferences.getInt(tabsKey, Tab.SEQUENCE_DEFAULT))
                val auxioDefault =
                    listOf(
                        Tab.Visible(MusicType.SONGS),
                        Tab.Visible(MusicType.ALBUMS),
                        Tab.Visible(MusicType.ARTISTS),
                        Tab.Visible(MusicType.PLAYLISTS),
                    )
                if (saved?.toList() == auxioDefault) {
                    remove(tabsKey)
                }
            }
            putBoolean(resetKey, true)
        }
    }

    override fun onSettingChanged(key: String, listener: HomeSettings.Listener) {
        when (key) {
            getString(R.string.set_key_home_tabs) -> {
                L.d("Dispatching tab setting change")
                listener.onTabsChanged()
            }
            getString(R.string.set_key_hide_collaborators) -> {
                L.d("Dispatching collaborator setting change")
                listener.onHideCollaboratorsChanged()
            }
        }
    }

    companion object {
        const val OLD_KEY_LIB_TABS = "auxio_lib_tabs"
    }
}
