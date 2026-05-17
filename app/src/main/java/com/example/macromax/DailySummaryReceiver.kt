package com.example.macromax

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailySummaryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs     = context.getSharedPreferences("macromax_prefs", Context.MODE_PRIVATE)
        val targetCal = prefs.getInt("target_calories", 0)
        if (targetCal == 0) return  // onboarding not complete yet

        val dateKey  = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val json     = prefs.getString("food_log_$dateKey", "[]") ?: "[]"
        val arr      = JSONArray(json)
        var consumed = 0
        for (i in 0 until arr.length()) {
            consumed += arr.getJSONObject(i).optInt("cal", 0)
        }
        val remaining = targetCal - consumed

        val title = context.getString(R.string.notif_summary_title)
        val body  = if (remaining > 0) {
            context.getString(R.string.notif_summary_remaining, remaining)
        } else {
            context.getString(R.string.notif_summary_goal_hit)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "daily_summary"
        const val NOTIF_ID   = 1001
    }
}
