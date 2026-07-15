package com.winlator.cmod.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.winlator.cmod.BuildConfig
import com.winlator.cmod.MainActivity
import com.winlator.cmod.R
import com.winlator.cmod.steam.service.SteamService

class NotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "pluvia_foreground_service"
        private const val CHANNEL_NAME = "Steam Background Service"
        private const val NOTIFICATION_ID = 1

        const val ACTION_EXIT = BuildConfig.APPLICATION_ID + ".EXIT"
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Background Steam downloads and sync"
            setShowBadge(false)
        }

        notificationManager.createNotificationChannel(channel)
    }

    fun notify(content: String) {
        val notification = createForegroundNotification(content)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun notifyProgress(appName: String, progress: Float, downloaded: String, total: String, speed: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopIntent = Intent(context, SteamService::class.java).apply { action = ACTION_EXIT }
        val stopPendingIntent = PendingIntent.getForegroundService(context, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(appName)
            .setContentText("$downloaded / $total — $speed")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(0, context.getString(android.R.string.cancel), stopPendingIntent)
            .setProgress(100, (progress * 100).toInt(), false)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun createForegroundNotification(content: String): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = Intent(context, SteamService::class.java).apply {
            action = ACTION_EXIT
        }
        val stopPendingIntent = PendingIntent.getForegroundService(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val appTitle = context.applicationInfo.loadLabel(context.packageManager).toString()

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(appTitle)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(0, context.getString(android.R.string.cancel), stopPendingIntent)
            .build()
    }
}
