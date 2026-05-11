package com.aiassistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aiassistant.AiAssistantApp
import com.aiassistant.MainActivity
import com.aiassistant.R
import com.aiassistant.manager.StopwatchManager
import com.aiassistant.manager.StopwatchState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class StopwatchService : Service() {

    @Inject
    lateinit var stopwatchManager: StopwatchManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeStateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, createNotification(isRunning = true),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, createNotification(isRunning = true))
                }
                observeState()
            }
            ACTION_PAUSE -> {
                updateNotification(isRunning = false)
            }
            ACTION_RESUME -> {
                updateNotification(isRunning = true)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeState() {
        observeStateJob?.cancel()
        observeStateJob = serviceScope.launch {
            stopwatchManager.elapsedSeconds.collect { seconds ->
                val isRunning = stopwatchManager.state.value == StopwatchState.RUNNING
                updateNotificationWithTime(seconds, isRunning)
            }
        }
    }

    private fun createNotification(isRunning: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, AiAssistantApp.CHANNEL_STOPWATCH)
            .setContentTitle(getString(R.string.stopwatch_notification_title))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (isRunning) {
            val pauseIntent = PendingIntent.getService(
                this,
                1,
                Intent(this, StopwatchService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.stopwatch_notification_action_pause),
                pauseIntent
            )
        } else {
            val resumeIntent = PendingIntent.getService(
                this,
                2,
                Intent(this, StopwatchService::class.java).apply { action = ACTION_RESUME },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                android.R.drawable.ic_media_play,
                getString(R.string.stopwatch_notification_action_resume),
                resumeIntent
            )
        }

        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, StopwatchService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.addAction(
            android.R.drawable.ic_delete,
            getString(R.string.stopwatch_notification_action_stop),
            stopIntent
        )

        return builder.build()
    }

    private fun updateNotification(isRunning: Boolean) {
        val notification = createNotification(isRunning)
        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationWithTime(elapsedSeconds: Long, isRunning: Boolean) {
        val hours = elapsedSeconds / 3600
        val minutes = (elapsedSeconds % 3600) / 60
        val seconds = elapsedSeconds % 60
        val timeText = String.format("%02d:%02d:%02d", hours, minutes, seconds)

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, AiAssistantApp.CHANNEL_STOPWATCH)
            .setContentTitle(getString(R.string.stopwatch_notification_title))
            .setContentText(timeText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (isRunning) {
            val pauseIntent = PendingIntent.getService(
                this,
                1,
                Intent(this, StopwatchService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.stopwatch_notification_action_pause),
                pauseIntent
            )
        } else {
            val resumeIntent = PendingIntent.getService(
                this,
                2,
                Intent(this, StopwatchService::class.java).apply { action = ACTION_RESUME },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                android.R.drawable.ic_media_play,
                getString(R.string.stopwatch_notification_action_resume),
                resumeIntent
            )
        }

        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, StopwatchService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.addAction(
            android.R.drawable.ic_delete,
            getString(R.string.stopwatch_notification_action_stop),
            stopIntent
        )

        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        const val ACTION_START = "com.aiassistant.action.STOPWATCH_START"
        const val ACTION_PAUSE = "com.aiassistant.action.STOPWATCH_PAUSE"
        const val ACTION_RESUME = "com.aiassistant.action.STOPWATCH_RESUME"
        const val ACTION_STOP = "com.aiassistant.action.STOPWATCH_STOP"
        const val NOTIFICATION_ID = 1001
    }
}
