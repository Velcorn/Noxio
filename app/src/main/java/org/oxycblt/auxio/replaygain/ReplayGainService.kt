/*
 * Copyright (c) 2026 Auxio Project
 * ReplayGainService.kt is part of Auxio.
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
 
package org.oxycblt.auxio.replaygain

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.oxycblt.auxio.R

/**
 * Keeps the process alive and shows the progress while the [ReplayGainScanner] works. Start it with
 * [start], stop the scan with [stop].
 */
@AndroidEntryPoint
class ReplayGainService : Service() {
    @Inject lateinit var scanner: ReplayGainScanner
    @Inject lateinit var tagWriter: ReplayGainTagWriter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scan: Job? = null
    private var progress: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scan?.cancel()
            finish()
            return START_NOT_STICKY
        }
        if (scan?.isActive == true) {
            return START_NOT_STICKY
        }
        val manager = NotificationManagerCompat.from(this)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.lbl_replay_gain_scan))
                .build()
        )
        // Noxio: the same service writes the measured values into the files.
        val writing = intent?.action == ACTION_WRITE
        val title =
            if (writing) R.string.lbl_replay_gain_writing else R.string.lbl_replay_gain_calculating
        ServiceCompat.startForeground(
            this,
            NOTIFICATION,
            notification(title, 0, 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        progress =
            if (writing) {
                tagWriter.state
                    .onEach {
                        if (it is TagWriteState.Running) {
                            @Suppress("MissingPermission")
                            manager.notify(NOTIFICATION, notification(title, it.done, it.total))
                        }
                    }
                    .launchIn(scope)
            } else {
                scanner.state
                    .onEach {
                        if (it is ScanState.Running) {
                            @Suppress("MissingPermission")
                            manager.notify(NOTIFICATION, notification(title, it.done, it.total))
                        }
                    }
                    .launchIn(scope)
            }
        scan = scope.launch {
            try {
                if (writing) tagWriter.write() else scanner.scan()
            } finally {
                finish()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Stops, and makes sure the notification is gone, also if a last update was still on its way.
     */
    private fun finish() {
        progress?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        NotificationManagerCompat.from(this).cancel(NOTIFICATION)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION)
        super.onDestroy()
    }

    private fun notification(title: Int, done: Int, total: Int) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_indexer_24)
            .setContentTitle(getString(title))
            .setContentText(getString(R.string.fmt_replay_gain_progress, done, total))
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                0,
                getString(R.string.lbl_cancel),
                android.app.PendingIntent.getService(
                    this,
                    0,
                    Intent(this, ReplayGainService::class.java).setAction(ACTION_STOP),
                    android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    companion object {
        private const val CHANNEL = "org.oxycblt.auxio.channel.REPLAYGAIN"
        private const val NOTIFICATION = 0xA0A2
        private const val ACTION_STOP = "de.devfin.noxio.replaygain.STOP"
        private const val ACTION_WRITE = "de.devfin.noxio.replaygain.WRITE"

        /** Start measuring in the background. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ReplayGainService::class.java),
            )
        }

        /** Write the values [ReplayGainTagWriter.plan] worked out into the files. */
        fun write(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ReplayGainService::class.java).setAction(ACTION_WRITE),
            )
        }

        /** Stop a running scan or write. What was done is kept. */
        fun stop(context: Context) {
            context.startService(
                Intent(context, ReplayGainService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
