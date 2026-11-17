/*
 * Copyright (c) 2026 Auxio Project
 * BackupNotification.kt is part of Auxio.
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
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.oxycblt.auxio.R
import org.oxycblt.auxio.util.newMainPendingIntent

/**
 * The warning that an automatic backup was not written because it holds much less than the biggest
 * saved one. It stays until the user decided in Settings > Backup, or a backup was written.
 *
 * @author Noxio
 */
object BackupNotification {
    private const val CHANNEL_ID = "org.oxycblt.auxio.channel.BACKUP"
    private const val NOTIFICATION_ID = 0xA0B0

    /** Tell the user that a backup was skipped. Nothing is shown without the permission. */
    fun showSkipped(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(
                    CHANNEL_ID,
                    NotificationManagerCompat.IMPORTANCE_DEFAULT,
                )
                .setName(context.getString(R.string.lbl_backup_notification_channel))
                .build(),
        )
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            // The same warning is shown in Settings > Backup.
            return
        }
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_save_24)
                .setContentTitle(context.getString(R.string.lbl_backup_skipped_title))
                .setContentText(context.getString(R.string.lng_backup_skipped))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.lng_backup_skipped)),
                )
                .setContentIntent(context.newMainPendingIntent())
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .build(),
        )
    }

    /** Remove the warning, it is not needed any more. */
    fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
