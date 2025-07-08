package com.cafetone.audio.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cafetone.audio.MainActivity
import com.cafetone.audio.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging Service
 * Handles push notifications for app updates, feature announcements, and user engagement
 */
class CafeToneMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "CafeToneMessaging"
        private const val UPDATE_CHANNEL_ID = "cafetone_updates"
        private const val ENGAGEMENT_CHANNEL_ID = "cafetone_engagement"
        private const val NOTIFICATION_ID_UPDATE = 1001
        private const val NOTIFICATION_ID_ENGAGEMENT = 1002
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        
        // Send token to your server or save it locally
        // You can also send this to Firebase Analytics as a user property
        saveTokenToPreferences(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "Message received from: ${remoteMessage.from}")
        
        // Handle different types of notifications
        val notificationType = remoteMessage.data["type"] ?: "general"
        
        when (notificationType) {
            "app_update" -> handleAppUpdateNotification(remoteMessage)
            "feature_announcement" -> handleFeatureAnnouncementNotification(remoteMessage)
            "engagement" -> handleEngagementNotification(remoteMessage)
            "github_milestone" -> handleGitHubMilestoneNotification(remoteMessage)
            else -> handleGeneralNotification(remoteMessage)
        }
    }

    private fun handleAppUpdateNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.data["title"] ?: "CaféTone Update Available"
        val body = remoteMessage.data["body"] ?: "A new version of CaféTone is available with exciting improvements!"
        val version = remoteMessage.data["version"] ?: ""
        val updateUrl = remoteMessage.data["update_url"] ?: ""
        
        val intent = if (updateUrl.isNotEmpty()) {
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(updateUrl))
        } else {
            Intent(this, MainActivity::class.java).apply {
                putExtra("action", "check_update")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cafe_mode)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$body\n\n🎵 New features in version $version:\n• Enhanced audio processing\n• Improved UI design\n• Better performance"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_info, "Update Now", pendingIntent)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_UPDATE, notification)
        
        Log.i(TAG, "App update notification shown")
    }

    private fun handleFeatureAnnouncementNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.data["title"] ?: "New CaféTone Feature"
        val body = remoteMessage.data["body"] ?: "Discover the latest enhancements to your audio experience!"
        val feature = remoteMessage.data["feature"] ?: ""
        
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("action", "show_feature")
            putExtra("feature", feature)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, ENGAGEMENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cafe_mode)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_ENGAGEMENT, notification)
        
        Log.i(TAG, "Feature announcement notification shown")
    }

    private fun handleEngagementNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.data["title"] ?: "Time for some Café Mode! ☕"
        val body = remoteMessage.data["body"] ?: "Transform your audio experience with Sony's premium DSP technology"
        
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("action", "engagement")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, ENGAGEMENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cafe_mode)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_ENGAGEMENT, notification)
        
        Log.i(TAG, "Engagement notification shown")
    }

    private fun handleGitHubMilestoneNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.data["title"] ?: "GitHub Milestone Reached! 🌟"
        val body = remoteMessage.data["body"] ?: "CaféTone has reached a new milestone on GitHub! Thank you for your support!"
        val stars = remoteMessage.data["stars"] ?: ""
        
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/evinjohnignatious/cafetone"))
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, ENGAGEMENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cafe_mode)
            .setContentTitle(title)
            .setContentText("$body ${if (stars.isNotEmpty()) "($stars stars)" else ""}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$body\n\n🙏 Thank you for being part of our community!"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_ENGAGEMENT, notification)
        
        Log.i(TAG, "GitHub milestone notification shown")
    }

    private fun handleGeneralNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "CaféTone"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "You have a new message"
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, ENGAGEMENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cafe_mode)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_ENGAGEMENT, notification)
        
        Log.i(TAG, "General notification shown")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Update notifications channel
            val updateChannel = NotificationChannel(
                UPDATE_CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications about app updates and new versions"
                enableLights(true)
                enableVibration(true)
            }
            
            // Engagement notifications channel
            val engagementChannel = NotificationChannel(
                ENGAGEMENT_CHANNEL_ID,
                "App Engagement",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Feature announcements and engagement notifications"
                enableLights(true)
                enableVibration(false)
            }
            
            notificationManager.createNotificationChannel(updateChannel)
            notificationManager.createNotificationChannel(engagementChannel)
        }
    }

    private fun saveTokenToPreferences(token: String) {
        val prefs = getSharedPreferences("cafetone_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()
        
        // You can also send this token to your backend server
        // to enable targeted notifications
        Log.d(TAG, "FCM token saved: $token")
    }
}