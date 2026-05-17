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

        tvSelectedDate   = findViewById(R.id.tvSelectedDate)
        cardDaySummary   = findViewById(R.id.cardDaySummary)
        tvDayTotalCal    = findViewById(R.id.tvDayTotalCal)
        tvDayMacros      = findViewById(R.id.tvDayMacros)
        containerEntries = findViewById(R.id.containerDayEntries)
        tvEmpty          = findViewById(R.id.tvHistoryEmpty)

        val calendarView = findViewById<CalendarView>(R.id.calendarView)

        // Prevent selecting future dates
        calendarView.maxDate = System.currentTimeMillis()

        // Show today's entries on open
        showEntriesForKey(todayDateKey())

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth)
            val key = SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
            showEntriesForKey(key)
        }
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

        for (i in 0 until arr.length()) {
            val obj   = arr.getJSONObject(i)
            val entry = FoodEntry(
                name     = obj.getString("name"),
                calories = obj.getInt("cal"),
                proteinG = obj.getInt("pro"),
                fatG     = obj.getInt("fat"),
                carbsG   = obj.getInt("car")
            )
            totalCal  += entry.calories
            totalPro  += entry.proteinG
            totalFat  += entry.fatG
            totalCarb += entry.carbsG

            val row = layoutInflater.inflate(
                R.layout.item_history_food_row, containerEntries, false
            )
            row.findViewById<TextView>(R.id.tvHistoryFoodName).text = entry.name
            row.findViewById<TextView>(R.id.tvHistoryFoodCal).text  = "${entry.calories} kcal"
            containerEntries.addView(row)
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
