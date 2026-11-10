/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimerNotification.kt is part of Auxio.
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
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Date
import org.oxycblt.auxio.R
import org.oxycblt.auxio.playback.service.PlaybackActions
import org.oxycblt.auxio.util.newBroadcastPendingIntent
import org.oxycblt.auxio.util.newMainPendingIntent

/**
 * A quiet notification of its own while a sleep timer runs, with a live countdown and buttons to
 * stop the timer or give it a few minutes more. The music notification is not touched.
 */
class SleepTimerNotification(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.lbl_sleep_notification_channel))
                .setShowBadge(false)
                .build()
        )
    }

    /** Show [state], or remove the notification when no timer runs. */
    fun show(state: SleepTimerState) {
        if (state !is SleepTimerState.Active) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            // Without the permission the countdown is still shown in the player.
            return
        }
        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sleep_timer_24)
                .setContentTitle(context.getString(R.string.lbl_sleep_timer))
                .setContentIntent(context.newMainPendingIntent())
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        when (state.mode) {
            is SleepTimerMode.Minutes -> {
                val endsAtWallClock = wallClockOf(state.endsAt)
                if (state.pausedRemainingMs != null) {
                    builder
                        .setShowWhen(false)
                        .setContentText(
                            context.getString(
                                R.string.fmt_sleep_notification_paused,
                                DateUtils.formatElapsedTime((state.remainingMs() + 999) / 1000),
                            )
                        )
                } else
                    builder
                        .setWhen(endsAtWallClock)
                        .setUsesChronometer(true)
                        .setChronometerCountDown(true)
                        .setContentText(
                            context.getString(
                                R.string.fmt_sleep_notification_until,
                                DateFormat.getTimeFormat(context).format(Date(endsAtWallClock)),
                            )
                        )
                builder.addAction(
                    0,
                    context.getString(
                        R.string.fmt_sleep_notification_extend,
                        SleepTimer.EXTEND_MINUTES,
                    ),
                    context.newBroadcastPendingIntent(PlaybackActions.ACTION_EXTEND_SLEEP_TIMER),
                )
            }
            SleepTimerMode.EndOfTrack ->
                builder
                    .setShowWhen(false)
                    .setContentText(context.getString(R.string.lbl_sleep_notification_end_of_track))
        }
        builder.addAction(
            0,
            context.getString(R.string.lbl_sleep_stop),
            context.newBroadcastPendingIntent(PlaybackActions.ACTION_STOP_SLEEP_TIMER),
        )
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun release() {
        manager.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "noxio_sleep_timer"
        private const val NOTIFICATION_ID = 0x5EE9

        /** The wall clock time of [elapsedRealtime], a point in [SystemClock.elapsedRealtime]. */
        fun wallClockOf(
            elapsedRealtime: Long,
            nowElapsed: Long = SystemClock.elapsedRealtime(),
            nowWallClock: Long = System.currentTimeMillis(),
        ) = nowWallClock + (elapsedRealtime - nowElapsed)
    }
}
