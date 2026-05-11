package com.aiassistant.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.aiassistant.AiAssistantApp
import com.aiassistant.MainActivity
import com.aiassistant.R

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(EXTRA_TODO_ID, -1)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "待办提醒"
        val reminderIndex = intent.getIntExtra(EXTRA_REMINDER_INDEX, 1)
        val reminderCount = intent.getIntExtra(EXTRA_REMINDER_COUNT, 1)
        val minutesBefore = intent.getIntExtra(EXTRA_MINUTES_BEFORE, 0)

        val openIntent = PendingIntent.getActivity(
            context,
            todoId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                putExtra("open_todo_id", todoId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = when {
            minutesBefore > 0 -> "$title (提前${minutesBefore}分钟)"
            reminderCount > 1 -> "$title (第${reminderIndex}/${reminderCount}次提醒)"
            else -> title
        }

        val notification = NotificationCompat.Builder(context, AiAssistantApp.CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("待办提醒")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_BASE_ID + todoId.toInt() + reminderIndex, notification)
    }

    companion object {
        const val EXTRA_TODO_ID = "extra_todo_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_REMINDER_INDEX = "extra_reminder_index"
        const val EXTRA_REMINDER_COUNT = "extra_reminder_count"
        const val EXTRA_MINUTES_BEFORE = "extra_minutes_before"
        const val NOTIFICATION_BASE_ID = 2000
    }
}
