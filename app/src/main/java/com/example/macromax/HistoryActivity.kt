package com.example.macromax

import android.os.Bundle
import android.view.View
import android.widget.CalendarView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var weeklyChart: WeeklyCalorieChartView
    private lateinit var tvSelectedDate: TextView
    private lateinit var cardDaySummary: MaterialCardView
    private lateinit var tvDayTotalCal: TextView
    private lateinit var tvDayMacros: TextView
    private lateinit var containerEntries: LinearLayout
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<ImageButton>(R.id.btnHistoryBack).setOnClickListener { finish() }

        weeklyChart      = findViewById(R.id.weeklyChart)
        tvSelectedDate   = findViewById(R.id.tvSelectedDate)
        cardDaySummary   = findViewById(R.id.cardDaySummary)
        tvDayTotalCal    = findViewById(R.id.tvDayTotalCal)
        tvDayMacros      = findViewById(R.id.tvDayMacros)
        containerEntries = findViewById(R.id.containerDayEntries)
        tvEmpty          = findViewById(R.id.tvHistoryEmpty)

        val calendarView = findViewById<CalendarView>(R.id.calendarView)

        // Prevent selecting future dates
        calendarView.maxDate = System.currentTimeMillis()

        // Load weekly chart
        loadWeeklyChart()

        // Show today's entries on open
        showEntriesForKey(todayDateKey())

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth)
            val key = SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
            showEntriesForKey(key)
        }
    }

    private fun loadWeeklyChart() {
        val prefs  = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val target = prefs.getInt("target_calories", 0)
        val fmt    = SimpleDateFormat("yyyyMMdd", Locale.US)
        val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())

        val bars = (6 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            val key  = fmt.format(cal.time)
            val json = prefs.getString("food_log_$key", "[]") ?: "[]"
            val arr  = JSONArray(json)
            var consumed = 0
            for (i in 0 until arr.length()) consumed += arr.getJSONObject(i).optInt("cal", 0)

            WeeklyCalorieChartView.DayBar(
                label    = dayFmt.format(cal.time).take(3),
                consumed = consumed,
                isToday  = daysAgo == 0
            )
        }

        weeklyChart.target = target
        weeklyChart.bars   = bars
    }

    private fun showEntriesForKey(dateKey: String) {
        tvSelectedDate.text = formatDate(dateKey)

        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val json  = prefs.getString("food_log_$dateKey", "[]") ?: "[]"
        val arr   = JSONArray(json)

        containerEntries.removeAllViews()

        if (arr.length() == 0) {
            cardDaySummary.visibility = View.GONE
            tvEmpty.visibility        = View.VISIBLE
            return
        }

        tvEmpty.visibility = View.GONE

        var totalCal = 0; var totalPro = 0; var totalFat = 0; var totalCarb = 0

        // Parse all entries
        val entries = (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            FoodEntry(
                name     = obj.getString("name"),
                calories = obj.getInt("cal"),
                proteinG = obj.getInt("pro"),
                fatG     = obj.getInt("fat"),
                carbsG   = obj.getInt("car"),
                mealType = obj.optString("meal", "other")
            )
        }

        entries.forEach { entry ->
            totalCal  += entry.calories
            totalPro  += entry.proteinG
            totalFat  += entry.fatG
            totalCarb += entry.carbsG
        }

        // Group by meal type and inflate with headers
        val mealOrder = listOf("breakfast", "lunch", "dinner", "snack", "other")
        val grouped   = entries.groupBy { it.mealType.lowercase().ifBlank { "other" } }

        for (mealType in mealOrder) {
            val group = grouped[mealType] ?: continue

            // Meal type header
            val header = layoutInflater.inflate(R.layout.item_meal_header, containerEntries, false)
            header.findViewById<TextView>(R.id.tvMealHeader).text =
                FoodLogAdapter.mealLabel(this, mealType)
            containerEntries.addView(header)

            // Food rows
            group.forEach { entry ->
                val row = layoutInflater.inflate(R.layout.item_history_food_row, containerEntries, false)
                row.findViewById<TextView>(R.id.tvHistoryFoodName).text = entry.name
                row.findViewById<TextView>(R.id.tvHistoryFoodCal).text  = "${entry.calories} kcal"
                containerEntries.addView(row)
            }
        }

        tvDayTotalCal.text      = totalCal.toString()
        tvDayMacros.text        = "P ${totalPro}g  ·  F ${totalFat}g  ·  C ${totalCarb}g"
        cardDaySummary.visibility = View.VISIBLE
    }

    private fun todayDateKey() =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun formatDate(key: String): String {
        return try {
            val parsed = SimpleDateFormat("yyyyMMdd", Locale.US).parse(key) ?: return key
            SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(parsed)
        } catch (e: Exception) {
            key
        }
    }
}
