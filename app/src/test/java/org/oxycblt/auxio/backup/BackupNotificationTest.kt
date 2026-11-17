/*
 * Copyright (c) 2026 Auxio Project
 * BackupNotificationTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.backup

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val notifications =
        shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)

    @Test
    fun aSkippedBackupIsToldAboutAndTheWarningGoesAwayAgain() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        BackupNotification.showSkipped(context)
        assertEquals(1, notifications.allNotifications.size)
        val shown = notifications.allNotifications.single()
        assertEquals(
            context.getString(R.string.lbl_backup_skipped_title),
            shown.extras.getString(android.app.Notification.EXTRA_TITLE),
        )
        BackupNotification.clear(context)
        assertEquals(0, notifications.allNotifications.size)
    }

    @Test
    fun withoutThePermissionNothingIsShown() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        BackupNotification.showSkipped(context)
        assertEquals(0, notifications.allNotifications.size)
    }
}
