package com.example.macromax

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MacroMaxMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID_REMINDERS = "macromax_reminders"
        const val CHANNEL_ID_UPDATES   = "macromax_updates"
    }

    // ── Token refresh ─────────────────────────────────────────────────────────

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Persist the new token to Firestore so the backend can target this device
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .update(
                mapOf(
                    "fcmToken"       to token,
                    "tokenUpdatedAt" to FieldValue.serverTimestamp()
                )
            )
            // If the doc doesn't exist yet, set it instead of update
            .addOnFailureListener {
                FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .set(mapOf("fcmToken" to token, "tokenUpdatedAt" to FieldValue.serverTimestamp()))
            }
    }

    // ── Incoming message ──────────────────────────────────────────────────────

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        ensureChannels()

        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)

        val body = message.notification?.body
            ?: message.data["body"]
            ?: return   // nothing to show

        // Tap opens MainActivity (or LoginActivity if signed out)
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Forward any deep-link extras from the data payload
            message.data.forEach { (k, v) -> putExtra(k, v) }
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Choose channel: "reminder" type vs. general updates
        val channelId = if (message.data["type"] == "reminder") CHANNEL_ID_REMINDERS
                        else CHANNEL_ID_UPDATES

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Use a stable notification ID based on content so duplicates replace each other
        val notifId = (title + body).hashCode()
        manager.notify(notifId, notification)
    }

    // ── Channel setup ─────────────────────────────────────────────────────────

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (manager.getNotificationChannel(CHANNEL_ID_REMINDERS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_REMINDERS,
                    getString(R.string.notif_channel_reminders_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = getString(R.string.notif_channel_reminders_desc)
                }
            )
        }

        if (manager.getNotificationChannel(CHANNEL_ID_UPDATES) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_UPDATES,
                    getString(R.string.notif_channel_updates_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notif_channel_updates_desc)
                }
            )
        }
    }
}
