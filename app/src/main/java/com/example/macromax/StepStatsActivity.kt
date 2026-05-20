package com.example.macromax

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StepStatsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_step_stats)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // Date label
        val dateFmt = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        findViewById<TextView>(R.id.tvStatsDate).text = dateFmt.format(Calendar.getInstance().time)

        loadStats()
    }

    private fun loadStats() {
        val prefs      = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val today      = todayKey()
        val steps      = prefs.getInt("steps_$today", 0)
        val goal       = prefs.getInt(SettingsActivity.PREF_STEP_GOAL, 10_000)
        val calories   = (steps * 0.04).toInt()
        val isImperial = prefs.getString(SettingsActivity.PREF_UNITS, "") == SettingsActivity.UNITS_IMPERIAL
        val distKm     = steps * 0.00076

        // Top stats
        findViewById<TextView>(R.id.tvStatSteps).text    = "%,d".format(steps)
        findViewById<TextView>(R.id.tvStatCalories).text = calories.toString()

        if (isImperial) {
            findViewById<TextView>(R.id.tvStatDistance).text     = "%.2f".format(distKm * 0.621371)
            findViewById<TextView>(R.id.tvStatDistanceUnit).text = "mi"
        } else {
            findViewById<TextView>(R.id.tvStatDistance).text     = "%.2f".format(distKm)
            findViewById<TextView>(R.id.tvStatDistanceUnit).text = "km"
        }

        // Goal progress bar
        val pct = if (goal > 0) ((steps * 100) / goal).coerceAtMost(100) else 0
        findViewById<ProgressBar>(R.id.pbStepGoal).progress = pct
        findViewById<TextView>(R.id.tvGoalProgress).text =
            "%,d / %,d steps".format(steps, goal)

        // Hourly chart
        val hourlyData = getHourlySteps(prefs, today)
        findViewById<HourlyBarChartView>(R.id.detailBarChart).apply {
            data     = hourlyData
            barColor = android.graphics.Color.parseColor("#CE93D8")
        }

        // Weekly rows
        buildWeeklyRows(prefs)
    }

    private fun buildWeeklyRows(prefs: android.content.SharedPreferences) {
        val container = findViewById<LinearLayout>(R.id.containerWeeklySteps)
        container.removeAllViews()
        val cal     = Calendar.getInstance()
        val keyFmt  = SimpleDateFormat("yyyyMMdd", Locale.US)
        val dayFmt  = SimpleDateFormat("EEE", Locale.getDefault())
        val dateFmt = SimpleDateFormat("d MMM", Locale.getDefault())
        val goal    = prefs.getInt(SettingsActivity.PREF_STEP_GOAL, 10_000)
        val dp      = resources.displayMetrics.density

        for (i in 6 downTo 0) {
            val daySteps = prefs.getInt("steps_${keyFmt.format(cal.time)}", 0)
            val isToday  = (i == 0)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
            }

            // Day label
            val tvDay = TextView(this).apply {
                text     = if (isToday) "Today" else dayFmt.format(cal.time)
                textSize = 13f
                if (isToday) setTypeface(null, Typeface.BOLD)
                alpha    = if (isToday) 1f else 0.6f
                layoutParams = LinearLayout.LayoutParams(
                    (56 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Date
            val tvDate = TextView(this).apply {
                text     = dateFmt.format(cal.time)
                textSize = 11f
                alpha    = 0.4f
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            // Mini progress bar
            val pb = ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal).apply {
                max      = 100
                progress = if (goal > 0) ((daySteps * 100) / goal).coerceAtMost(100) else 0
                progressDrawable.setColorFilter(
                    android.graphics.Color.parseColor("#CE93D8"),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                layoutParams = LinearLayout.LayoutParams(
                    (80 * dp).toInt(), (4 * dp).toInt()
                ).also { it.marginEnd = (10 * dp).toInt() }
            }

            // Steps label
            val tvSteps = TextView(this).apply {
                text     = if (daySteps > 0) "%,d".format(daySteps) else "—"
                textSize = 13f
                if (isToday) setTypeface(null, Typeface.BOLD)
                alpha    = if (daySteps > 0) 1f else 0.3f
                gravity  = Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    (72 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            row.addView(tvDay)
            row.addView(tvDate)
            row.addView(pb)
            row.addView(tvSteps)
            container.addView(row)

            // Divider (not after last)
            if (i > 0) {
                val divider = android.view.View(this).apply {
                    setBackgroundColor(
                        androidx.core.content.ContextCompat.getColor(
                            this@StepStatsActivity, R.color.divider_line
                        )
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    )
                }
                container.addView(divider)
            }

            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
    }

    private fun getHourlySteps(
        prefs: android.content.SharedPreferences,
        today: String
    ): IntArray {
        val snaps = IntArray(24) { h -> prefs.getInt("steps_snap_${today}_$h", 0) }
        return IntArray(24) { h ->
            if (h == 0) snaps[0]
            else (snaps[h] - snaps[h - 1]).coerceAtLeast(0)
        }
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(java.util.Date())
}
