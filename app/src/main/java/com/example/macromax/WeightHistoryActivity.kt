package com.example.macromax

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WeightEntry(val dateKey: String, val weightKg: Float)

class WeightHistoryActivity : AppCompatActivity() {

    private lateinit var tvStatStart:   TextView
    private lateinit var tvStatCurrent: TextView
    private lateinit var tvStatChange:  TextView
    private lateinit var tvChartEmpty:  TextView
    private lateinit var weightChart:   WeightLineChartView
    private lateinit var tilWeightToday: TextInputLayout
    private lateinit var etWeightToday: TextInputEditText
    private lateinit var btnSaveWeight: MaterialButton
    private lateinit var containerEntries: LinearLayout
    private lateinit var tvHistoryEmpty: TextView

    private val prefs get() = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
    private val weightUnit get() = prefs.getString("weight_unit", "kg") ?: "kg"
    private val goal       get() = prefs.getString("user_goal", "maintain") ?: "maintain"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weight_history)

        findViewById<ImageButton>(R.id.btnWeightBack).setOnClickListener { finish() }

        tvStatStart      = findViewById(R.id.tvStatStart)
        tvStatCurrent    = findViewById(R.id.tvStatCurrent)
        tvStatChange     = findViewById(R.id.tvStatChange)
        tvChartEmpty     = findViewById(R.id.tvChartEmpty)
        weightChart      = findViewById(R.id.weightChart)
        tilWeightToday   = findViewById(R.id.tilWeightToday)
        etWeightToday    = findViewById(R.id.etWeightToday)
        btnSaveWeight    = findViewById(R.id.btnSaveWeight)
        containerEntries = findViewById(R.id.containerWeightEntries)
        tvHistoryEmpty   = findViewById(R.id.tvHistoryEmpty)

        // Unit hint on the text field
        tilWeightToday.hint = weightUnit

        btnSaveWeight.setOnClickListener { saveWeight() }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    // ── Data ────────────────────────────────────────────────────────────────

    private fun loadData() {
        val entries = loadEntries()
        updateStats(entries)
        updateChart(entries)
        updateHistory(entries)
        prefillToday(entries)
    }

    private fun loadEntries(): List<WeightEntry> {
        return prefs.all
            .filter { it.key.startsWith("weight_log_") }
            .mapNotNull { (key, value) ->
                val dateKey = key.removePrefix("weight_log_")
                val kg = value as? Float ?: return@mapNotNull null
                WeightEntry(dateKey, kg)
            }
            .sortedBy { it.dateKey }
    }

    private fun saveWeight() {
        val text = etWeightToday.text.toString().trim()
        val value = text.toFloatOrNull()
        if (value == null || value <= 0f) {
            etWeightToday.error = getString(R.string.error_required)
            return
        }
        val kg = if (weightUnit == "lbs") value / 2.205f else value
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        prefs.edit().putFloat("weight_log_$today", kg).apply()
        loadData()
        Snackbar.make(btnSaveWeight, getString(R.string.weight_saved), Snackbar.LENGTH_SHORT).show()
    }

    // ── Stats ────────────────────────────────────────────────────────────────

    private fun updateStats(entries: List<WeightEntry>) {
        if (entries.isEmpty()) {
            tvStatStart.text   = "—"
            tvStatCurrent.text = "—"
            tvStatChange.text  = "—"
            return
        }
        val start   = entries.first().weightKg
        val current = entries.last().weightKg
        val delta   = current - start

        tvStatStart.text   = formatWeight(start)
        tvStatCurrent.text = formatWeight(current)

        val sign = if (delta >= 0f) "+" else "−"
        tvStatChange.text = "$sign${formatWeight(Math.abs(delta))}"

        // Color the change: green = progress toward goal, red = away
        val isPositive = delta > 0f
        val goodForGoal = when (goal) {
            "lose"  -> !isPositive
            "gain"  -> isPositive
            else    -> false   // maintain: neutral
        }
        tvStatChange.setTextColor(when {
            delta == 0f  -> tvStatCurrent.currentTextColor
            goodForGoal  -> Color.parseColor("#4CAF50")
            else         -> Color.parseColor("#EF5350")
        })
    }

    // ── Chart ─────────────────────────────────────────────────────────────────

    private fun updateChart(entries: List<WeightEntry>) {
        if (entries.isEmpty()) {
            weightChart.visibility  = View.GONE
            tvChartEmpty.visibility = View.VISIBLE
            return
        }
        tvChartEmpty.visibility = View.GONE
        weightChart.visibility  = View.VISIBLE

        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val fmt   = SimpleDateFormat("yyyyMMdd", Locale.US)
        val disp  = SimpleDateFormat("d MMM",    Locale.getDefault())

        val points = entries.takeLast(60).map { e ->
            val date = fmt.parse(e.dateKey) ?: Date()
            WeightLineChartView.WeightPoint(
                label    = disp.format(date),
                weightKg = e.weightKg,
                isToday  = e.dateKey == today
            )
        }
        weightChart.points = points
    }

    // ── History list ──────────────────────────────────────────────────────────

    private fun updateHistory(entries: List<WeightEntry>) {
        containerEntries.removeAllViews()
        if (entries.isEmpty()) {
            tvHistoryEmpty.visibility = View.VISIBLE
            return
        }
        tvHistoryEmpty.visibility = View.GONE

        val fmt  = SimpleDateFormat("yyyyMMdd",      Locale.US)
        val disp = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

        // Show newest first, up to 90 entries
        val reversed = entries.reversed().take(90)
        reversed.forEachIndexed { i, entry ->
            val prevKg = if (i < reversed.size - 1) reversed[i + 1].weightKg else null
            val row = buildEntryRow(entry, prevKg, fmt, disp)
            containerEntries.addView(row)

            // Thin divider between rows
            if (i < reversed.size - 1) {
                val div = View(this)
                div.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (0.75f * resources.displayMetrics.density).toInt()
                ).apply { setMargins(0, 0, 0, 0) }
                div.setBackgroundColor(getColor(R.color.divider_line))
                containerEntries.addView(div)
            }
        }
    }

    private fun buildEntryRow(
        entry: WeightEntry,
        prevKg: Float?,
        fmt: SimpleDateFormat,
        disp: SimpleDateFormat
    ): View {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }

        // Date label
        val tvDate = TextView(this).apply {
            val date = fmt.parse(entry.dateKey) ?: Date()
            text = if (entry.dateKey == today) getString(R.string.weight_today) else disp.format(date)
            textSize = 13f
            setTextColor(currentTextColor)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Weight value
        val tvWeight = TextView(this).apply {
            text = formatWeight(entry.weightKg)
            textSize = 13f
            setTextColor(currentTextColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        // Delta indicator
        val tvDelta = TextView(this).apply {
            if (prevKg != null) {
                val delta = entry.weightKg - prevKg
                if (Math.abs(delta) > 0.01f) {
                    val sign = if (delta > 0f) "▲" else "▼"
                    text = " $sign ${formatWeight(Math.abs(delta))}"
                    val goodForGoal = when (goal) {
                        "lose" -> delta < 0f
                        "gain" -> delta > 0f
                        else   -> false
                    }
                    setTextColor(if (goodForGoal) Color.parseColor("#4CAF50") else Color.parseColor("#EF5350"))
                    textSize = 11f
                } else {
                    text = ""
                }
            } else {
                text = ""
            }
        }

        row.addView(tvDate)
        row.addView(tvWeight)
        row.addView(tvDelta)
        return row
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun prefillToday(entries: List<WeightEntry>) {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val todayEntry = entries.find { it.dateKey == today }
        if (todayEntry != null) {
            val display = if (weightUnit == "lbs") todayEntry.weightKg * 2.205f else todayEntry.weightKg
            etWeightToday.setText(String.format("%.1f", display))
        }
    }

    private fun formatWeight(kg: Float): String {
        return if (weightUnit == "lbs") {
            String.format("%.1f", kg * 2.205f)
        } else {
            String.format("%.1f", kg)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
