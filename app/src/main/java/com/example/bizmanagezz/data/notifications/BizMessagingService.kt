package com.example.bizmanagezz.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.bizmanagezz.MainActivity
import com.example.bizmanagezz.R
import com.example.bizmanagezz.data.repository.SettingsStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class BizMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FirebaseAuth.getInstance().currentUser?.uid?.let {
            NotificationRegistration.schedule(applicationContext, it)
        }

        // Backend registration will use the latest Firebase token.
        // Never write the token to application logs.
        getSharedPreferences(
            "bizmanage_messaging",
            MODE_PRIVATE
        ).edit()
            .putBoolean("registration_required", true)
            .apply()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val user = FirebaseAuth.getInstance().currentUser
            ?: return

        // The backend must send account-scoped data messages.
        // Drop messages for a previous account on this device.
        if (message.data["accountId"] != user.uid) {
            return
        }

        val notificationsEnabled = runCatching {
            SettingsStore(applicationContext)
                .readSettings()
                .notificationsEnabled
        }.getOrDefault(false)

        if (!notificationsEnabled) {
            return
        }

        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (
            !NotificationManagerCompat.from(this)
                .areNotificationsEnabled()
        ) {
            return
        }

        createChannel()

        val title = message.data["title"]
            ?.take(100)
            ?: "BizManage SA"

        val body = message.data["body"]
            ?.take(300)
            ?: "You have a business update."

        val notificationId = (
                message.messageId
                    ?: message.data["eventId"]
                    ?: "${System.currentTimeMillis()}"
                ).hashCode()

        val intent = Intent(
            this,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val publicNotification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(
                    R.drawable.ic_business_notification
                )
                .setContentTitle("BizManage SA")
                .setContentText("You have a business update.")
                .build()

        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(
                    R.drawable.ic_business_notification
                )
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(body)
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setVisibility(
                    NotificationCompat.VISIBILITY_PRIVATE
                )
                .setPublicVersion(publicNotification)
                .setPriority(
                    NotificationCompat.PRIORITY_DEFAULT
                )
                .build()

        try {
            NotificationManagerCompat.from(this)
                .notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission may change between checking and posting.
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Business updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description =
                    "Stock alerts and business notifications"
            }

            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "bizmanage_business_updates"
    }
}