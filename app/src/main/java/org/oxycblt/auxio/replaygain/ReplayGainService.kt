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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scan: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scan?.cancel()
            stopSelf()
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
        ServiceCompat.startForeground(
            this,
            NOTIFICATION,
            notification(0, 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        scanner.state
            .onEach {
                if (it is ScanState.Running) {
                    @Suppress("MissingPermission")
                    manager.notify(NOTIFICATION, notification(it.done, it.total))
                }
            }
            .launchIn(scope)
        scan = scope.launch {
            try {
                scanner.scan()
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(done: Int, total: Int) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_indexer_24)
            .setContentTitle(getString(R.string.lbl_replay_gain_scan))
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

        /** Start measuring in the background. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ReplayGainService::class.java),
            )
        }

        /** Stop a running scan. What was measured is kept. */
        fun stop(context: Context) {
            context.startService(
                Intent(context, ReplayGainService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
