/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimerNotificationTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.timer

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SleepTimerNotificationTest {
    @Test
    fun pausingReplacesTheChronometerWithFrozenTimeAndResumingRestoresIt() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager =
            shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        val notification = SleepTimerNotification(app)
        val active =
            SleepTimerState.Active(
                SleepTimerMode.Minutes(100),
                SystemClock.elapsedRealtime() + 6_000_000L,
                true,
            )
        notification.show(active)
        assertTrue(
            manager.allNotifications.single().extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        )
        notification.show(active.copy(pausedRemainingMs = 5_999_500L))
        val paused = manager.allNotifications.single()
        assertFalse(paused.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(
            paused.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("1:40:00")
        )
        assertEquals(2, paused.actions.size)
        notification.show(active)
        assertTrue(
            manager.allNotifications.single().extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        )
        notification.show(SleepTimerState.Off)
        assertEquals(0, manager.allNotifications.size)
    }
}
