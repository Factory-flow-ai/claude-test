package com.momenttrack.scanner

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import androidx.work.WorkManager

class MomentTrackApp : Application(), Configuration.Provider {

    companion object {
        const val CHANNEL_ID_SCANNER = "scanner_service"
        const val CHANNEL_ID_SYNC = "sync_notifications"
        lateinit var instance: MomentTrackApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val scannerChannel = NotificationChannel(
                CHANNEL_ID_SCANNER,
                "Scanner Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when the scanner is actively monitoring"
                setShowBadge(false)
            }

            val syncChannel = NotificationChannel(
                CHANNEL_ID_SYNC,
                "Sync Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications about data synchronization"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(scannerChannel)
            notificationManager.createNotificationChannel(syncChannel)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
