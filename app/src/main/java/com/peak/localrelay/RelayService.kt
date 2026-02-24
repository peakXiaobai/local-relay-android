package com.peak.localrelay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RelayService : Service() {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val relayEngine = RelayEngine()
    private var startJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch {
                    stopRelay("Stopped by user")
                    stopSelf()
                }
            }

            ACTION_START -> {
                val config = parseConfig(intent)
                RelayPrefs.save(this, config)
                RelayController.setConfig(config)
                RelayController.clearError()

                startForeground(NOTIFICATION_ID, buildNotification(config, "Starting relay..."))

                startJob?.cancel()
                startJob = scope.launch {
                    startRelay(config)
                }
            }

            else -> {
                // Ignore unknown action.
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        startJob?.cancel()
        runBlocking {
            relayEngine.stop()
        }
        RelayController.setStatus(RelayStatus.STOPPED)
        log("Relay service destroyed")
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun startRelay(config: RelayConfig) {
        RelayController.setStatus(RelayStatus.STARTING)
        log("Starting relay -> ${config.targetBaseUrl}")

        try {
            relayEngine.start(config) { line ->
                log(line)
            }

            RelayController.setStatus(RelayStatus.RUNNING)
            val bindHost = if (config.bindAllInterfaces) "0.0.0.0" else "127.0.0.1"
            updateNotification(config, "Listening on $bindHost:${config.localPort}")
            log("Relay running on $bindHost:${config.localPort}")
        } catch (error: Throwable) {
            RelayController.setStatus(RelayStatus.ERROR, error.message ?: "Unknown error")
            log("Failed to start relay: ${error.message}")
            updateNotification(config, "Relay failed: ${error.message}")
            stopSelf()
        }
    }

    private suspend fun stopRelay(reason: String) {
        runCatching {
            relayEngine.stop()
        }.onFailure {
            log("Stop error: ${it.message}")
        }
        RelayController.setStatus(RelayStatus.STOPPED)
        log(reason)
    }

    private fun parseConfig(intent: Intent): RelayConfig {
        val current = RelayPrefs.load(this)

        val target = intent.getStringExtra(EXTRA_TARGET_URL)?.trim().orEmpty()
        val port = intent.getIntExtra(EXTRA_LOCAL_PORT, current.localPort)
        val bindAll = intent.getBooleanExtra(EXTRA_BIND_ALL, current.bindAllInterfaces)

        return RelayConfig(
            targetBaseUrl = if (target.isBlank()) current.targetBaseUrl else target,
            localPort = if (port in 1..65535) port else current.localPort,
            bindAllInterfaces = bindAll,
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(config: RelayConfig, contentText: String): Notification {
        val stopIntent = Intent(this, RelayService::class.java).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText\nTarget: ${config.targetBaseUrl}"),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.notification_stop), stopPendingIntent)
            .build()
    }

    private fun updateNotification(config: RelayConfig, text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(config, text))
    }

    private fun log(line: String) {
        val now = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        RelayController.appendLog("[$now] $line")
    }

    companion object {
        private const val CHANNEL_ID = "relay_service"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.peak.localrelay.action.START"
        const val ACTION_STOP = "com.peak.localrelay.action.STOP"

        const val EXTRA_TARGET_URL = "target_url"
        const val EXTRA_LOCAL_PORT = "local_port"
        const val EXTRA_BIND_ALL = "bind_all"

        fun start(context: Context, config: RelayConfig) {
            val intent = Intent(context, RelayService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TARGET_URL, config.targetBaseUrl)
                putExtra(EXTRA_LOCAL_PORT, config.localPort)
                putExtra(EXTRA_BIND_ALL, config.bindAllInterfaces)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RelayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
