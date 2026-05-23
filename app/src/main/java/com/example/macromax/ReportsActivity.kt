package com.example.macromax

import android.graphics.Color
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.ChipGroup
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReportsActivity : AppCompatActivity() {

    private enum class Period { WEEK, MONTH }
    private var currentPeriod = Period.WEEK

    private lateinit var tvAvgCal: TextView
    private lateinit var tvCalTarget: TextView
    private lateinit var tvOnTarget: TextView
    private lateinit var pbCalAvg: ProgressBar
    private lateinit var tvDaysLogged: TextView

    private lateinit var tvAvgPro: TextView
    private lateinit var tvTargetPro: TextView
    private lateinit var pbRepProtein: ProgressBar
    private lateinit var tvAvgFat: TextView
    private lateinit var tvTargetFat: TextView
    private lateinit var pbRepFat: ProgressBar
    private lateinit var tvAvgCarbs: TextView
    private lateinit var tvTargetCarbs: TextView
    private lateinit var pbRepCarbs: ProgressBar

    private lateinit var tvTotalSessions: TextView
    private lateinit var tvTotalWorkoutCal: TextView

    private lateinit var tvCurrentStreak: TextView
    private lateinit var tvConsistency: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        BottomNavHelper.setup(this, R.id.navReports)
        findViewById<ImageButton>(R.id.btnReportsBack).setOnClickListener { finish() }

        tvAvgCal          = findViewById(R.id.tvAvgCal)
        tvCalTarget        = findViewById(R.id.tvCalTarget)
        tvOnTarget         = findViewById(R.id.tvOnTarget)
        pbCalAvg           = findViewById(R.id.pbCalAvg)
        tvDaysLogged       = findViewById(R.id.tvDaysLogged)
        tvAvgPro           = findViewById(R.id.tvAvgPro)
        tvTargetPro        = findViewById(R.id.tvTargetPro)
        pbRepProtein       = findViewById(R.id.pbRepProtein)
        tvAvgFat           = findViewById(R.id.tvAvgFat)
        tvTargetFat        = findViewById(R.id.tvTargetFat)
        pbRepFat           = findViewById(R.id.pbRepFat)
        tvAvgCarbs         = findViewById(R.id.tvAvgCarbs)
        tvTargetCarbs      = findViewById(R.id.tvTargetCarbs)
        pbRepCarbs         = findViewById(R.id.pbRepCarbs)
        tvTotalSessions    = findViewById(R.id.tvTotalSessions)
        tvTotalWorkoutCal  = findViewById(R.id.tvTotalWorkoutCal)
        tvCurrentStreak    = findViewById(R.id.tvCurrentStreak)
        tvConsistency      = findViewById(R.id.tvConsistency)

        val cgPeriod = findViewById<ChipGroup>(R.id.cgPeriod)
        cgPeriod.setOnCheckedStateChangeListener { _, checkedIds ->
            currentPeriod = if (checkedIds.contains(R.id.chipMonth)) Period.MONTH else Period.WEEK
            refreshStats()
        }

        refreshStats()
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    private data class Stats(
        val totalDays: Int,
        val daysLogged: Int,
        val avgCal: Int,
        val avgPro: Int,
        val avgFat: Int,
        val avgCarbs: Int,
        val daysOnTarget: Int,
        val workoutSessions: Int,
        val workoutCalories: Int
    )

    private fun getDateKeys(): List<String> {
        val fmt   = SimpleDateFormat("yyyyMMdd", Locale.US)
        val cal   = Calendar.getInstance()
        val today = Calendar.getInstance()
        val keys  = mutableListOf<String>()
        when (currentPeriod) {
            Period.WEEK -> {
                repeat(7) {
                    keys.add(0, fmt.format(cal.time))
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                }
            }
            Period.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                while (!cal.after(today)) {
                    keys.add(fmt.format(cal.time))
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }
        return keys
    }

    private fun computeStats(dateKeys: List<String>): Stats {
        val prefs      = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val calTarget  = prefs.getInt("target_calories",  2000)
        var sumCal = 0; var sumPro = 0; var sumFat = 0; var sumCarbs = 0
        var daysLogged = 0; var daysOnTarget = 0
        var workoutSessions = 0; var workoutCal = 0

        for (key in dateKeys) {
            val arr = JSONArray(prefs.getString("food_log_$key", "[]") ?: "[]")
            if (arr.length() > 0) {
                var dayCal = 0; var dayPro = 0; var dayFat = 0; var dayCarbs = 0
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    dayCal   += o.optInt("cal", 0)
                    dayPro   += o.optInt("pro", 0)
                    dayFat   += o.optInt("fat", 0)
                    dayCarbs += o.optInt("car", 0)
                }
                daysLogged++
                sumCal += dayCal; sumPro += dayPro; sumFat += dayFat; sumCarbs += dayCarbs
                if (dayCal >= calTarget * 0.8 && dayCal <= calTarget * 1.2) daysOnTarget++
            }
            val workouts = WorkoutRepository.load(prefs, key)
            workoutSessions += workouts.size
            workoutCal      += workouts.sumOf { it.caloriesBurned }
        }

        return Stats(
            totalDays        = dateKeys.size,
            daysLogged       = daysLogged,
            avgCal           = if (daysLogged > 0) sumCal   / daysLogged else 0,
            avgPro           = if (daysLogged > 0) sumPro   / daysLogged else 0,
            avgFat           = if (daysLogged > 0) sumFat   / daysLogged else 0,
            avgCarbs         = if (daysLogged > 0) sumCarbs / daysLogged else 0,
            daysOnTarget     = daysOnTarget,
            workoutSessions  = workoutSessions,
            workoutCalories  = workoutCal
        )
    }

    private fun currentStreak(): Int {
        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val fmt   = SimpleDateFormat("yyyyMMdd", Locale.US)
        val cal   = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1) // start from yesterday
        var streak = 0
        repeat(365) {
            val key = fmt.format(cal.time)
            val arr = JSONArray(prefs.getString("food_log_$key", "[]") ?: "[]")
            if (arr.length() > 0) { streak++; cal.add(Calendar.DAY_OF_YEAR, -1) }
            else return streak
        }
        return streak
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun refreshStats() {
        val prefs     = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val dateKeys  = getDateKeys()
        val stats     = computeStats(dateKeys)
        val calTarget = prefs.getInt("target_calories",  2000)
        val proTarget = prefs.getInt("target_protein_g", 0)
        val fatTarget = prefs.getInt("target_fat_g",     0)
        val carbTarget= prefs.getInt("target_carbs_g",   0)

        // ── Calories ──────────────────────────────────────────────────────────
        tvAvgCal.text    = stats.avgCal.toString()
        tvCalTarget.text = calTarget.toString()

        val onTargetPct = if (stats.daysLogged > 0)
            (stats.daysOnTarget * 100 / stats.daysLogged) else 0
        tvOnTarget.text      = "$onTargetPct%"
        tvOnTarget.setTextColor(
            when {
                onTargetPct >= 60 -> Color.parseColor("#4CAF50")
                onTargetPct >= 30 -> Color.parseColor("#FF9800")
                else              -> Color.parseColor("#EF5350")
            }
        )
        pbCalAvg.progress = if (calTarget > 0)
            (stats.avgCal * 100 / calTarget).coerceAtMost(100) else 0

        tvDaysLogged.text = getString(R.string.reports_days_logged, stats.daysLogged, stats.totalDays)

        // ── Macros ────────────────────────────────────────────────────────────
        tvAvgPro.text    = "${stats.avgPro}g"
        tvTargetPro.text = if (proTarget > 0) "/ ${proTarget}g" else ""
        pbRepProtein.progress = if (proTarget > 0)
            (stats.avgPro * 100 / proTarget).coerceAtMost(100) else 0

        tvAvgFat.text    = "${stats.avgFat}g"
        tvTargetFat.text = if (fatTarget > 0) "/ ${fatTarget}g" else ""
        pbRepFat.progress = if (fatTarget > 0)
            (stats.avgFat * 100 / fatTarget).coerceAtMost(100) else 0

        tvAvgCarbs.text    = "${stats.avgCarbs}g"
        tvTargetCarbs.text = if (carbTarget > 0) "/ ${carbTarget}g" else ""
        pbRepCarbs.progress = if (carbTarget > 0)
            (stats.avgCarbs * 100 / carbTarget).coerceAtMost(100) else 0

        // ── Workouts ──────────────────────────────────────────────────────────
        tvTotalSessions.text   = stats.workoutSessions.toString()
        tvTotalWorkoutCal.text = stats.workoutCalories.toString()

        // ── Streak & consistency ──────────────────────────────────────────────
        tvCurrentStreak.text = currentStreak().toString()
        tvConsistency.text   = "${stats.daysLogged}/${stats.totalDays}"
    }
}
