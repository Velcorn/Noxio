/*
 * Copyright (c) 2026 Auxio Project
 * LauncherIcon.kt is part of Auxio.
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
 
package org.oxycblt.auxio.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The icons the app can have in the launcher. Each one is an activity alias in the manifest with
 * its own icon, and exactly one of them is enabled at a time.
 *
 * @param intCode The value stored in the settings.
 * @param aliasName The name of the activity alias, relative to the code namespace.
 */
enum class LauncherIcon(val intCode: Int, private val aliasName: String) {
    /** The full color icon. */
    DEFAULT(0xA400, "MainActivityDefault"),
    /** A flat white silhouette on black. */
    MINIMAL_DARK(0xA401, "MainActivityMinimalDark"),
    /** A flat black silhouette on white. */
    MINIMAL_LIGHT(0xA402, "MainActivityMinimalLight");

    private fun component(context: Context) =
        ComponentName(context.packageName, "org.oxycblt.auxio.$aliasName")

    companion object {
        fun fromIntCode(code: Int) = entries.firstOrNull { it.intCode == code }

        /**
         * Make [icon] the launcher icon. The new one is enabled before the others are disabled, so
         * there is never a moment without a launcher entry. The app is not restarted.
         */
        fun apply(context: Context, icon: LauncherIcon) {
            val packageManager = context.packageManager
            packageManager.setComponentEnabledSetting(
                icon.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            for (other in entries) {
                if (other != icon) {
                    packageManager.setComponentEnabledSetting(
                        other.component(context),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        }
    }
}
