package com.aiassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aiassistant.data.dao.AppLogDao
import com.aiassistant.data.worker.MemoryDecayWorker
import com.aiassistant.util.AppLogger
import java.util.concurrent.TimeUnit
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class AiAssistantApp : Application(), Configuration.Provider {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HiltWorkerFactoryEntryPoint {
        fun workerFactory(): HiltWorkerFactory
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppLoggerEntryPoint {
        fun appLogDao(): AppLogDao
    }

    override fun onCreate() {
        super.onCreate()
        initPdfBox()
        createNotificationChannels()
        initAppLogger()
        scheduleMemoryDecay()
    }

    override val workManagerConfiguration: Configuration
        get() {
            val workerFactory = EntryPointAccessors.fromApplication(
                applicationContext,
                HiltWorkerFactoryEntryPoint::class.java
            ).workerFactory()
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
        }

    private fun initPdfBox() {
        try {
            val clazz = Class.forName("com.tom_roush.pdfbox.android.PDFBoxResourceLoader")
            val initMethod = clazz.getMethod("init", android.content.Context::class.java)
            initMethod.invoke(null, applicationContext)
        } catch (e: Exception) {
            Log.w("AiAssistantApp", "PDFBox initialization skipped: ${e.message}")
        }
    }

    private fun createNotificationChannels() {
        val stopwatchChannel = NotificationChannel(
            CHANNEL_STOPWATCH,
            getString(R.string.channel_stopwatch),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_stopwatch_desc)
        }

        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            getString(R.string.channel_reminder),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.channel_reminder_desc)
            enableVibration(true)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannels(
            listOf(stopwatchChannel, reminderChannel)
        )
    }

    private fun scheduleMemoryDecay() {
        val workRequest = PeriodicWorkRequestBuilder<MemoryDecayWorker>(
            1, TimeUnit.DAYS
        ).setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "memory_decay",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun initAppLogger() {
        try {
            val appLogDao = EntryPointAccessors.fromApplication(
                applicationContext,
                AppLoggerEntryPoint::class.java
            ).appLogDao()
            AppLogger.init(appLogDao)
            AppLogger.installGlobalExceptionHandler()
        } catch (e: Exception) {
            Log.w("AiAssistantApp", "AppLogger initialization failed: ${e.message}")
        }
    }

    companion object {
        const val CHANNEL_STOPWATCH = "stopwatch_channel"
        const val CHANNEL_REMINDER = "reminder_channel"
    }
}
