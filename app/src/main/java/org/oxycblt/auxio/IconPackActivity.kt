/*
 * Copyright (c) 2026 Auxio Project
 * IconPackActivity.kt is part of Auxio.
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
 
package org.oxycblt.auxio

import android.app.Activity
import android.os.Bundle

/**
 * Makes Noxio show up as an icon pack in launchers that support them, like Kvaesitso. The pack only
 * contains the minimal Noxio icon (see `res/xml/appfilter.xml`). Launchers read the pack's files
 * directly and never open this activity, but if one does, it closes right away.
 */
class IconPackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
