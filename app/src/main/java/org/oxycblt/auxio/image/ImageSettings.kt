/*
 * Copyright (c) 2023 Auxio Project
 * ImageSettings.kt is part of Auxio.
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
 
package org.oxycblt.auxio.image

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.oxycblt.auxio.settings.Settings

/**
 * User configuration specific to image loading.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
interface ImageSettings : Settings<ImageSettings.Listener> {
    /** The strategy to use when loading album covers. */
    val coverMode: CoverMode
    /** Whether to force all album covers to have a 1:1 aspect ratio. */
    val forceSquareCovers: Boolean

    interface Listener {
        /** Called when [coverMode] changes. */
        fun onImageSettingsChanged() {}
    }
}

class ImageSettingsImpl @Inject constructor(@ApplicationContext context: Context) :
    Settings.Impl<ImageSettings.Listener>(context), ImageSettings {
    override val coverMode: CoverMode
        get() = CoverMode.BALANCED

    override val forceSquareCovers: Boolean
        get() = false

    override fun migrate() {
        // Artwork quality and cover proportions are fixed, even after restoring an old backup.
        sharedPreferences.edit {
            remove("auxio_square_covers")
            remove("auxio_cover_mode2")
            remove("auxio_cover_mode")
            remove("KEY_SHOW_COVERS")
            remove("KEY_QUALITY_COVERS")
        }
    }
}
