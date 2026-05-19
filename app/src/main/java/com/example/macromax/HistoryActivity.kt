package com.example.macromax

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HistoryActivity : AppCompatActivity() {

    private lateinit var weeklyChart:     WeeklyCalorieChartView
    private lateinit var cardDatePicker:  MaterialCardView
    private lateinit var tvSelectedDate:  TextView
    private lateinit var cardDaySummary:  MaterialCardView
    private lateinit var cardEntries:     MaterialCardView
    private lateinit var tvDayTotalCal:   TextView
    private lateinit var tvDayMacros:     TextView
    private lateinit var containerEntries: LinearLayout
    private lateinit var tvEmpty:         TextView
    private lateinit var etHistorySearch: TextInputEditText
    private lateinit var scrollHistory:   ScrollView
    private lateinit var scrollSearchResults: ScrollView
    private lateinit var containerSearchResults: LinearLayout

    // Track the currently displayed date key so the picker preselects it
    private var currentDateKey = todayDateKey()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<ImageButton>(R.id.btnHistoryBack).setOnClickListener { finish() }

        weeklyChart          = findViewById(R.id.weeklyChart)
        cardDatePicker       = findViewById(R.id.cardDatePicker)
        tvSelectedDate       = findViewById(R.id.tvSelectedDate)
        cardDaySummary       = findViewById(R.id.cardDaySummary)
        cardEntries          = findViewById(R.id.cardEntries)
        tvDayTotalCal        = findViewById(R.id.tvDayTotalCal)
        tvDayMacros          = findViewById(R.id.tvDayMacros)
        containerEntries     = findViewById(R.id.containerDayEntries)
        tvEmpty              = findViewById(R.id.tvHistoryEmpty)
        etHistorySearch      = findViewById(R.id.etHistorySearch)
        scrollHistory        = findViewById(R.id.scrollHistory)
        scrollSearchResults  = findViewById(R.id.scrollSearchResults)
        containerSearchResults = findViewById(R.id.containerSearchResults)

        loadWeeklyChart()
        showEntriesForKey(currentDateKey)

        cardDatePicker.setOnClickListener { openDatePicker() }

        etHistorySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim() ?: ""
                onSearchQueryChanged(q)
            }
        })
    }

    // ── Date picker ───────────────────────────────────────────────────────────

    private fun openDatePicker() {
        // Preselect the currently shown date (convert local date key → UTC ms)
        val utcFmt = SimpleDateFormat("yyyyMMdd", Locale.US).also {
            it.timeZone = TimeZone.getTimeZone("UTC")
        }
        val preselect = try {
            utcFmt.parse(currentDateKey)?.time
                ?: MaterialDatePicker.todayInUtcMilliseconds()
        } catch (e: Exception) {
            MaterialDatePicker.todayInUtcMilliseconds()
        }

        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointBackward.now())
            .build()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.history_pick_date))
            .setSelection(preselect)
            .setCalendarConstraints(constraints)
            .build()

        picker.addOnPositiveButtonClickListener { selectionMs ->
            // MaterialDatePicker returns UTC midnight; extract date parts in UTC
            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            utcCal.timeInMillis = selectionMs
            val key = String.format(
                "%04d%02d%02d",
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH) + 1,
                utcCal.get(Calendar.DAY_OF_MONTH)
            )
            currentDateKey = key
            showEntriesForKey(key)
        }

        picker.show(supportFragmentManager, "history_date_picker")
    }

    // ── Weekly chart ──────────────────────────────────────────────────────────

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

    // ── Day detail ────────────────────────────────────────────────────────────

    private fun showEntriesForKey(dateKey: String) {
        currentDateKey    = dateKey
        tvSelectedDate.text = formatDate(dateKey)

        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val json  = prefs.getString("food_log_$dateKey", "[]") ?: "[]"
        val arr   = JSONArray(json)

        containerEntries.removeAllViews()

        if (arr.length() == 0) {
            cardDaySummary.visibility = View.GONE
            cardEntries.visibility    = View.GONE
            tvEmpty.visibility        = View.VISIBLE
            return
        }

        tvEmpty.visibility = View.GONE

        var totalCal = 0; var totalPro = 0; var totalFat = 0; var totalCarb = 0

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

        entries.forEach {
            totalCal  += it.calories
            totalPro  += it.proteinG
            totalFat  += it.fatG
            totalCarb += it.carbsG
        }

        // Group by meal type and build rows
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
            group.forEachIndexed { idx, entry ->
                val row = layoutInflater.inflate(R.layout.item_history_food_row, containerEntries, false)
                row.findViewById<TextView>(R.id.tvHistoryFoodName).text  = entry.name
                row.findViewById<TextView>(R.id.tvHistoryFoodCal).text   = "${entry.calories}"
                row.findViewById<TextView>(R.id.tvHistoryFoodMacros).text =
                    "P ${entry.proteinG}g  ·  F ${entry.fatG}g  ·  C ${entry.carbsG}g"
                containerEntries.addView(row)

                // Thin divider between rows (not after last in group)
                if (idx < group.size - 1) {
                    val div = View(this)
                    val lp  = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (0.75f * resources.displayMetrics.density).toInt()
                    ).apply { setMargins(16.dp, 0, 16.dp, 0) }
                    div.layoutParams = lp
                    div.setBackgroundColor(getColor(R.color.divider_line))
                    containerEntries.addView(div)
                }
            }
        }

        tvDayTotalCal.text = totalCal.toString()
        tvDayMacros.text   = "P ${totalPro}g  ·  F ${totalFat}g  ·  C ${totalCarb}g"
        cardDaySummary.visibility = View.VISIBLE
        cardEntries.visibility    = View.VISIBLE
    }

    // ── History search ────────────────────────────────────────────────────────

    private fun onSearchQueryChanged(query: String) {
        if (query.length < 2) {
            scrollHistory.visibility       = View.VISIBLE
            scrollSearchResults.visibility = View.GONE
            return
        }
        scrollHistory.visibility       = View.GONE
        scrollSearchResults.visibility = View.VISIBLE
        performHistorySearch(query)
    }

    private fun performHistorySearch(query: String) {
        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val fmt   = SimpleDateFormat("yyyyMMdd", Locale.US)
        val dispFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        containerSearchResults.removeAllViews()

        data class Hit(val dateKey: String, val entry: FoodEntry)

        val hits = mutableListOf<Hit>()
        val cal  = Calendar.getInstance()
        val q    = query.lowercase(Locale.getDefault())

        for (d in 0..364) {
            if (d > 0) cal.add(Calendar.DAY_OF_YEAR, -1)
            val key = fmt.format(cal.time)
            val arr = JSONArray(prefs.getString("food_log_$key", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val obj  = arr.getJSONObject(i)
                val name = obj.optString("name", "")
                if (name.lowercase(Locale.getDefault()).contains(q)) {
                    hits.add(Hit(key, FoodEntry(
                        name     = name,
                        calories = obj.optInt("cal"),
                        proteinG = obj.optInt("pro"),
                        fatG     = obj.optInt("fat"),
                        carbsG   = obj.optInt("car"),
                        mealType = obj.optString("meal", "other")
                    )))
                }
            }
            if (hits.size >= 100) break
        }

        if (hits.isEmpty()) {
            val tv = TextView(this).apply {
                text    = getString(R.string.history_search_no_results)
                textSize = 13f
                alpha   = 0.45f
                gravity = android.view.Gravity.CENTER
                setPadding(0, (32 * resources.displayMetrics.density).toInt(), 0, 0)
            }
            containerSearchResults.addView(tv)
            return
        }

        var lastKey = ""
        for (hit in hits) {
            // Date label when key changes
            if (hit.dateKey != lastKey) {
                lastKey = hit.dateKey
                val label = try {
                    val d = fmt.parse(hit.dateKey)
                    if (hit.dateKey == fmt.format(Date())) "Today"
                    else dispFmt.format(d!!)
                } catch (e: Exception) { hit.dateKey }
                val tv = TextView(this).apply {
                    text      = label
                    textSize  = 11f
                    alpha     = 0.5f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, (10 * resources.displayMetrics.density).toInt(),
                        0, (4 * resources.displayMetrics.density).toInt())
                }
                containerSearchResults.addView(tv)
            }
            // Entry row
            val row = layoutInflater.inflate(R.layout.item_history_food_row, containerSearchResults, false)
            row.findViewById<TextView>(R.id.tvHistoryFoodName).text  = hit.entry.name
            row.findViewById<TextView>(R.id.tvHistoryFoodCal).text   = "${hit.entry.calories}"
            row.findViewById<TextView>(R.id.tvHistoryFoodMacros).text =
                "P ${hit.entry.proteinG}g  ·  F ${hit.entry.fatG}g  ·  C ${hit.entry.carbsG}g"
            containerSearchResults.addView(row)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun todayDateKey() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun formatDate(key: String): String {
        return try {
            val parsed = SimpleDateFormat("yyyyMMdd", Locale.US).parse(key) ?: return key
            val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            if (key == today) {
                getString(R.string.weight_today) + " — " +
                SimpleDateFormat("MMMM d", Locale.getDefault()).format(parsed)
            } else {
                SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(parsed)
            }
        } catch (e: Exception) {
            key
        }
    }
}
