/*
 * Copyright (c) 2026 Auxio Project
 * HomeSettingsFragment.kt is part of Auxio.
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
 
package org.oxycblt.auxio.settings

import android.content.Context
import android.content.res.XmlResourceParser
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.home.HomeViewModel
import org.oxycblt.auxio.settings.ui.BottomAlignedPreferenceLayoutManager
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.systemBarInsetsCompat
import org.xmlpull.v1.XmlPullParser

/**
 * Noxio: the list of Settings as the last page of the home tabs, so it can be reached by swiping
 * past the music tabs. Its rows open the same screens as the Settings screen does.
 */
@AndroidEntryPoint
class HomeSettingsFragment : PreferenceFragmentCompat() {
    private val homeModel: HomeViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_root, rootKey)
    }

    private var rootScreen: PreferenceScreen? = null
    private var entries: List<SettingsSearch.Entry>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The tabs say where this is, so the title bar of the Settings screen and the room it
        // keeps under the list are left out.
        view.findViewById<View>(R.id.preferences_bar)?.isVisible = false
        view.findViewById<View>(android.R.id.list_container)?.updateLayoutParams<
            ViewGroup.MarginLayoutParams,
        > {
            bottomMargin = 0
        }
        rootScreen = preferenceScreen
        collectImmediately(homeModel.settingsQuery, ::showMatches)
    }

    /** The settings that match [query] from all screens, or the plain list without one. */
    private fun showMatches(query: String) {
        val root = rootScreen ?: return
        if (query.isBlank()) {
            if (preferenceScreen !== root) preferenceScreen = root
            return
        }
        val matches = SettingsSearch.match(entries ?: collectEntries().also { entries = it }, query)
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)
        val screenTitles = SCREENS.associate { (key, _, title) ->
            getString(key) to getString(title)
        }
        // The best match last, next to the search field at the bottom.
        for (match in matches.asReversed()) {
            screen.addPreference(
                Preference(context).apply {
                    key = match.screenKey
                    title = match.title
                    // Where the setting is, or for a row of this list itself what it holds.
                    val screenTitle = screenTitles[match.screenKey]
                    summary =
                        if (screenTitle == null || screenTitle == match.title) match.summary
                        else screenTitle
                    isIconSpaceReserved = false
                },
            )
        }
        if (matches.isEmpty()) {
            screen.addPreference(
                Preference(context).apply {
                    title = getString(R.string.lbl_no_settings_found)
                    isSelectable = false
                    isIconSpaceReserved = false
                },
            )
        }
        preferenceScreen = screen
        // Show the end of the list, where the best match is.
        listView?.post { listView?.scrollToPosition((listView?.adapter?.itemCount ?: 1) - 1) }
    }

    /** Every setting of every screen, read from the same files the screens show. */
    private fun collectEntries(): List<SettingsSearch.Entry> {
        val context = preferenceManager.context
        val found = mutableListOf<SettingsSearch.Entry>()
        rootScreen?.let { root ->
            for (i in 0 until root.preferenceCount) {
                val row = root.getPreference(i)
                val key = row.key ?: continue
                found +=
                    SettingsSearch.Entry(
                        row.title.toString(),
                        row.summary?.toString().orEmpty(),
                        key,
                    )
            }
        }
        for ((key, xml, _) in SCREENS) {
            val screenKey = getString(key)
            resources.getXml(xml).use { parser ->
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    // Groups only hold settings, the settings are what is searched.
                    if (
                        parser.name.endsWith("PreferenceCategory") ||
                            parser.name.endsWith("PreferenceScreen")
                    )
                        continue
                    if (!parser.getAttributeBooleanValue(APP, "isPreferenceVisible", true)) continue
                    val title = parser.text(context, "title") ?: continue
                    found +=
                        SettingsSearch.Entry(
                            title,
                            parser.text(context, "summary").orEmpty(),
                            screenKey,
                        )
                }
            }
        }
        return found
    }

    /** A text attribute of a setting, in the app's or Android's namespace, as a resource. */
    private fun XmlResourceParser.text(context: Context, name: String): String? {
        val res =
            getAttributeResourceValue(APP, name, 0).takeIf { it != 0 }
                ?: getAttributeResourceValue(ANDROID, name, 0)
        return if (res != 0) context.getString(res) else null
    }

    private companion object {
        const val APP = "http://schemas.android.com/apk/res-auto"
        const val ANDROID = "http://schemas.android.com/apk/res/android"

        /** The screens of Settings: the key of their row, their file and their title. */
        val SCREENS =
            listOf(
                Triple(R.string.set_key_ui, R.xml.preferences_ui, R.string.set_ui),
                Triple(R.string.set_key_music, R.xml.preferences_music, R.string.set_library),
                Triple(R.string.set_key_audio, R.xml.preferences_audio, R.string.set_audio),
                Triple(
                    R.string.set_key_backup_settings,
                    R.xml.preferences_backup,
                    R.string.set_backup,
                ),
            )
    }

    override fun onCreateRecyclerView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?,
    ) =
        super.onCreateRecyclerView(inflater, parent, savedInstanceState).apply {
            clipToPadding = false
            layoutManager = BottomAlignedPreferenceLayoutManager(context)
            // The bottom inset of the home pages covers the playing strip and the tabs.
            // While typing a search, the list ends above the keyboard, which the search row and
            // the tabs are lifted above too.
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.systemBarInsetsCompat
                val compat = WindowInsetsCompat.toWindowInsetsCompat(insets, view)
                val ime = compat.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val nav = compat.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                val row = resources.getDimensionPixelSize(R.dimen.size_detail_bottom_bar)
                val bottom = if (ime > 0) ime + bars.bottom - nav + 2 * row else bars.bottom
                updatePadding(top = bars.top, bottom = bottom)
                insets
            }
        }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val key = preference.key ?: return super.onPreferenceTreeClick(preference)
        homeModel.showSettingsScreen(key)
        return true
    }
}
