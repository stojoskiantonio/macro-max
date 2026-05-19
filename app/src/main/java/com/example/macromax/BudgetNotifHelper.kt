package com.example.macromax

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages the persistent "calorie budget" notification that lives in the
 * notification shade and updates whenever food is logged.
 */
object BudgetNotifHelper {

    private const val CHANNEL_ID = "budget_notif"
    private const val NOTIF_ID   = 1002

    /**
     * Fast path — called from MainActivity after refreshFoodLog() already
     * computed the daily totals; no file I/O needed.
     */
    fun update(
        context: Context,
        totalCal: Int, totalPro: Int, totalFat: Int, totalCarb: Int
    ) {
        val prefs = context.getSharedPreferences("macromax_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(SettingsActivity.PREF_BUDGET_NOTIF_ENABLED, false)) return

        val targetCal  = prefs.getInt("target_calories",  0)
        val targetPro  = prefs.getInt("target_protein_g", 0)
        val targetFat  = prefs.getInt("target_fat_g",     0)
        val targetCarb = prefs.getInt("target_carbs_g",   0)

        show(context, totalCal, totalPro, totalFat, totalCarb,
            targetCal, targetPro, targetFat, targetCarb)
    }

    /**
     * Slow path — reads the food log from SharedPrefs itself.
     * Used when enabling the toggle from SettingsActivity (no totals available).
     */
    fun refresh(context: Context) {
        val prefs   = context.getSharedPreferences("macromax_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(SettingsActivity.PREF_BUDGET_NOTIF_ENABLED, false)) return

        val dateKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val json    = prefs.getString("food_log_$dateKey", "[]") ?: "[]"
        val arr     = JSONArray(json)

        var totalCal = 0; var totalPro = 0; var totalFat = 0; var totalCarb = 0
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            totalCal  += obj.getInt("cal")
            totalPro  += obj.getInt("pro")
            totalFat  += obj.getInt("fat")
            totalCarb += obj.getInt("car")
        }

        val targetCal  = prefs.getInt("target_calories",  0)
        val targetPro  = prefs.getInt("target_protein_g", 0)
        val targetFat  = prefs.getInt("target_fat_g",     0)
        val targetCarb = prefs.getInt("target_carbs_g",   0)

        show(context, totalCal, totalPro, totalFat, totalCarb,
            targetCal, targetPro, targetFat, targetCarb)
    }

    /** Cancel the persistent notification (called when toggle is turned off). */
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun show(
        context: Context,
        totalCal: Int, totalPro: Int, totalFat: Int, totalCarb: Int,
        targetCal: Int, targetPro: Int, targetFat: Int, targetCarb: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        ensureChannel(context)

        // Title: remaining kcal or "goal reached"
        val title = when {
            targetCal > 0 && totalCal >= targetCal ->
                context.getString(R.string.notif_budget_goal_hit)
            targetCal > 0 ->
                context.getString(R.string.notif_budget_remaining, (targetCal - totalCal))
            else ->
                context.getString(R.string.notif_budget_consumed, totalCal)
        }

        // Body: macros remaining (or consumed if no targets)
        val body = if (targetCal > 0) {
            val remPro  = (targetPro  - totalPro).coerceAtLeast(0)
            val remFat  = (targetFat  - totalFat).coerceAtLeast(0)
            val remCarb = (targetCarb - totalCarb).coerceAtLeast(0)
            context.getString(R.string.notif_budget_macros_remaining, remPro, remFat, remCarb)
        } else {
            context.getString(R.string.notif_budget_macros_consumed, totalPro, totalFat, totalCarb)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bar_chart)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)          // persistent — stays in shade
            .setOnlyAlertOnce(true)    // silent updates after first show
            .setSilent(true)           // no sound/vibration on update
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_budget_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_budget_channel_desc)
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }
}
