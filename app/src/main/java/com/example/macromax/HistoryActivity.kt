package com.example.macromax

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<ImageButton>(R.id.btnHistoryBack).setOnClickListener { finish() }

        val rv    = findViewById<RecyclerView>(R.id.rvHistory)
        val empty = findViewById<TextView>(R.id.tvHistoryEmpty)

        rv.layoutManager = LinearLayoutManager(this)

        val days = loadHistory()

        if (days.isEmpty()) {
            empty.visibility = View.VISIBLE
            rv.visibility    = View.GONE
        } else {
            empty.visibility = View.GONE
            rv.visibility    = View.VISIBLE
            rv.adapter       = HistoryDayAdapter(days)
        }
    }

    private fun loadHistory(): List<DayHistory> {
        val prefs   = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val today   = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

        // Collect all food_log_YYYYMMDD keys, newest first, skip today
        val dayKeys = prefs.all.keys
            .filter { it.startsWith("food_log_") }
            .map    { it.removePrefix("food_log_") }
            .filter { it != today }          // today is shown on the home screen
            .sortedDescending()

        return dayKeys.mapNotNull { key ->
            val json = prefs.getString("food_log_$key", "[]") ?: "[]"
            val arr  = JSONArray(json)
            if (arr.length() == 0) return@mapNotNull null   // skip empty days

            val entries = mutableListOf<FoodEntry>()
            var totalCal = 0; var totalPro = 0; var totalFat = 0; var totalCarb = 0

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val e = FoodEntry(
                    name     = obj.getString("name"),
                    calories = obj.getInt("cal"),
                    proteinG = obj.getInt("pro"),
                    fatG     = obj.getInt("fat"),
                    carbsG   = obj.getInt("car")
                )
                entries.add(e)
                totalCal  += e.calories
                totalPro  += e.proteinG
                totalFat  += e.fatG
                totalCarb += e.carbsG
            }

            DayHistory(
                dateKey      = key,
                displayDate  = formatDate(key),
                entries      = entries,
                totalCalories = totalCal,
                totalProtein  = totalPro,
                totalFat      = totalFat,
                totalCarbs    = totalCarb
            )
        }
    }

    private fun formatDate(key: String): String {
        return try {
            val parsed = SimpleDateFormat("yyyyMMdd", Locale.US).parse(key) ?: return key
            SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(parsed)
        } catch (e: Exception) {
            key
        }
    }
}
