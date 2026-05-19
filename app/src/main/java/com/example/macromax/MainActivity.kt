package com.example.macromax

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null

    private lateinit var tvGreeting: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvStepCount: TextView
    private lateinit var tvCaloriesBurned: TextView
    private lateinit var stepDonut: StepDonutView

    private lateinit var macroDonut: MacroDonutView
    private lateinit var tvProteinVal: TextView
    private lateinit var tvFatVal: TextView
    private lateinit var tvCarbsVal: TextView
    private lateinit var pbProtein: ProgressBar
    private lateinit var pbFat: ProgressBar
    private lateinit var pbCarbs: ProgressBar
    private lateinit var tvTotalConsumed: TextView
    private lateinit var tvNetCalories: TextView
    private lateinit var tvNoFood: TextView
    private lateinit var rvFoodLog: RecyclerView

    private var consumedCalToday = 0
    private var burnedCalToday   = 0

    private lateinit var tvWaterCount: TextView
    private lateinit var tvWaterGlasses: TextView
    private lateinit var pbWater: ProgressBar

    private lateinit var tvWorkoutsEmpty: TextView
    private lateinit var containerWorkouts: LinearLayout

    private var workoutCaloriesToday = 0

    companion object {
        private const val PERMISSION_REQUEST_ACTIVITY = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ── Bind views ───────────────────────────────────────────────────────
        tvGreeting       = findViewById(R.id.tvGreeting)
        tvStreak         = findViewById(R.id.tvStreak)
        macroDonut       = findViewById(R.id.macroDonut)
        tvProteinVal     = findViewById(R.id.tvProteinVal)
        tvFatVal         = findViewById(R.id.tvFatVal)
        tvCarbsVal       = findViewById(R.id.tvCarbsVal)
        pbProtein        = findViewById(R.id.pbProtein)
        pbFat            = findViewById(R.id.pbFat)
        pbCarbs          = findViewById(R.id.pbCarbs)
        tvTotalConsumed  = findViewById(R.id.tvTotalConsumed)
        tvNetCalories    = findViewById(R.id.tvNetCalories)
        tvNoFood         = findViewById(R.id.tvNoFood)
        rvFoodLog        = findViewById(R.id.rvFoodLog)
        tvStepCount      = findViewById(R.id.tvStepCount)
        tvCaloriesBurned = findViewById(R.id.tvCaloriesBurned)
        stepDonut        = findViewById(R.id.stepDonut)
        tvWaterCount     = findViewById(R.id.tvWaterCount)
        tvWaterGlasses   = findViewById(R.id.tvWaterGlasses)
        pbWater          = findViewById(R.id.pbWater)
        tvWorkoutsEmpty  = findViewById(R.id.tvWorkoutsEmpty)
        containerWorkouts = findViewById(R.id.containerWorkouts)

        // ── RecyclerView ──────────────────────────────────────────────────────
        rvFoodLog.layoutManager = LinearLayoutManager(this)

        // ── FAB ───────────────────────────────────────────────────────────────
        findViewById<FloatingActionButton>(R.id.fabAddFood).setOnClickListener {
            startActivity(Intent(this, LogFoodActivity::class.java))
        }

        // ── Bottom navigation ─────────────────────────────────────────────────
        findViewById<ImageButton>(R.id.navHome).setOnClickListener { /* already here */ }
        findViewById<ImageButton>(R.id.navHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<ImageButton>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<ImageButton>(R.id.navReports).setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }
        findViewById<ImageButton>(R.id.navSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // ── Workout logging ───────────────────────────────────────────────────
        findViewById<MaterialButton>(R.id.btnLogWorkout).setOnClickListener {
            startActivity(Intent(this, WorkoutLogActivity::class.java))
        }

        // ── Water tracker ─────────────────────────────────────────────────────
        findViewById<MaterialButton>(R.id.btnWaterPlus).setOnClickListener {
            adjustWater(+1)
        }
        findViewById<MaterialButton>(R.id.btnWaterMinus).setOnClickListener {
            adjustWater(-1)
        }

        // ── Step sensor ───────────────────────────────────────────────────────
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor    = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor != null) requestActivityPermissionIfNeeded()

        // Restore saved step count
        val prefs    = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val todayKey = todayDateKey()
        updateStepUI(prefs.getInt("steps_$todayKey", 0))

        // ── Daily summary notification ────────────────────────────────────────
        scheduleDailySummaryNotification()
    }

    override fun onResume() {
        super.onResume()
        updateGreeting()
        refreshFoodLog()
        refreshWater()
        refreshStreak()
        refreshWorkouts()

        stepSensor?.also { sensor ->
            if (hasActivityPermission()) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // ── Greeting ─────────────────────────────────────────────────────────────

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        tvGreeting.text = when {
            hour < 12 -> "Good morning! ☀️"
            hour < 18 -> "Good afternoon! 🌤"
            else      -> "Good evening! 🌙"
        }
    }

    // ── Streak ───────────────────────────────────────────────────────────────

    private fun refreshStreak() {
        val prefs  = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val streak = calculateStreak(prefs)
        if (streak > 0) {
            tvStreak.text       = getString(R.string.streak_days, streak)
            tvStreak.visibility = View.VISIBLE
        } else {
            tvStreak.visibility = View.GONE
        }
    }

    private fun calculateStreak(prefs: android.content.SharedPreferences): Int {
        var streak = 0
        val cal    = Calendar.getInstance()
        // Start from yesterday — today may not be complete yet
        cal.add(Calendar.DAY_OF_YEAR, -1)
        repeat(365) {
            val key  = SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
            val json = prefs.getString("food_log_$key", "[]") ?: "[]"
            if (JSONArray(json).length() > 0) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                return streak
            }
        }
        return streak
    }

    // ── Water tracker ────────────────────────────────────────────────────────

    private fun waterKey() = "water_glasses_${todayDateKey()}"

    private fun waterGoal(): Int =
        getSharedPreferences("macromax_prefs", MODE_PRIVATE).getInt("water_goal", 8)

    private fun adjustWater(delta: Int) {
        val prefs   = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val current = prefs.getInt(waterKey(), 0)
        val updated = (current + delta).coerceAtLeast(0)
        prefs.edit().putInt(waterKey(), updated).apply()
        updateWaterUI(updated)
    }

    private fun refreshWater() {
        val prefs   = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val glasses = prefs.getInt(waterKey(), 0)
        updateWaterUI(glasses)
    }

    private fun updateWaterUI(glasses: Int) {
        val goal = waterGoal()
        tvWaterCount.text   = "$glasses / $goal"
        tvWaterGlasses.text = getString(R.string.water_glasses_label, glasses)
        pbWater.progress    = if (goal > 0) (glasses * 100 / goal).coerceAtMost(100) else 0
    }

    // ── Food log ─────────────────────────────────────────────────────────────

    private fun refreshFoodLog() {
        val prefs      = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val targetCal  = prefs.getInt("target_calories",  0)
        val targetPro  = prefs.getInt("target_protein_g", 0)
        val targetFat  = prefs.getInt("target_fat_g",     0)
        val targetCarb = prefs.getInt("target_carbs_g",   0)

        val logKey = "food_log_${todayDateKey()}"
        val json   = prefs.getString(logKey, "[]") ?: "[]"
        val arr    = JSONArray(json)

        val entries   = mutableListOf<FoodEntry>()
        var totalCal  = 0
        var totalPro  = 0
        var totalFat  = 0
        var totalCarb = 0

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val entry = FoodEntry(
                name      = obj.getString("name"),
                calories  = obj.getInt("cal"),
                proteinG  = obj.getInt("pro"),
                fatG      = obj.getInt("fat"),
                carbsG    = obj.getInt("car"),
                mealType  = obj.optString("meal", "other")
            )
            entries.add(entry)
            totalCal  += entry.calories
            totalPro  += entry.proteinG
            totalFat  += entry.fatG
            totalCarb += entry.carbsG
        }

        // Macro donut
        macroDonut.totalCalories  = totalCal
        macroDonut.targetCalories = targetCal
        macroDonut.proteinG       = totalPro
        macroDonut.fatG           = totalFat
        macroDonut.carbG          = totalCarb

        // Labels
        tvProteinVal.text = if (targetPro > 0) "${totalPro}g / ${targetPro}g" else "${totalPro}g"
        tvFatVal.text     = if (targetFat > 0) "${totalFat}g / ${targetFat}g" else "${totalFat}g"
        tvCarbsVal.text   = if (targetCarb > 0) "${totalCarb}g / ${targetCarb}g" else "${totalCarb}g"

        // Progress bars (capped at 100%)
        pbProtein.progress = if (targetPro > 0) ((totalPro  * 100) / targetPro).coerceAtMost(100)  else 0
        pbFat.progress     = if (targetFat > 0) ((totalFat  * 100) / targetFat).coerceAtMost(100)  else 0
        pbCarbs.progress   = if (targetCarb > 0) ((totalCarb * 100) / targetCarb).coerceAtMost(100) else 0

        // Header total
        tvTotalConsumed.text = if (targetCal > 0) "$totalCal / $targetCal kcal" else "$totalCal kcal"

        // Per-meal calorie targets (from Settings splits)
        val mealTargets: Map<String, Int> = if (targetCal > 0) {
            val b = prefs.getInt(SettingsActivity.PREF_MEAL_SPLIT_BREAKFAST, SettingsActivity.DEFAULT_SPLIT_BREAKFAST)
            val l = prefs.getInt(SettingsActivity.PREF_MEAL_SPLIT_LUNCH,     SettingsActivity.DEFAULT_SPLIT_LUNCH)
            val d = prefs.getInt(SettingsActivity.PREF_MEAL_SPLIT_DINNER,    SettingsActivity.DEFAULT_SPLIT_DINNER)
            val s = prefs.getInt(SettingsActivity.PREF_MEAL_SPLIT_SNACK,     SettingsActivity.DEFAULT_SPLIT_SNACK)
            mapOf(
                "breakfast" to (targetCal * b / 100),
                "lunch"     to (targetCal * l / 100),
                "dinner"    to (targetCal * d / 100),
                "snack"     to (targetCal * s / 100)
            )
        } else emptyMap()

        // Net calories
        consumedCalToday = totalCal
        updateNetCaloriesDisplay()

        // Meal breakdown card (shown only when calorie targets are set)
        updateMealBreakdown(entries, mealTargets)

        // Live budget notification (no-op if disabled in Settings)
        BudgetNotifHelper.update(this, totalCal, totalPro, totalFat, totalCarb)

        // Food list — grouped by meal type
        if (entries.isEmpty()) {
            tvNoFood.visibility  = View.VISIBLE
            rvFoodLog.visibility = View.GONE
        } else {
            tvNoFood.visibility  = View.GONE
            rvFoodLog.visibility = View.VISIBLE
            rvFoodLog.adapter    = FoodLogAdapter(
                items         = FoodLogAdapter.buildItems(entries, mealTargets),
                onEditClick   = { rawIndex, entry -> showEditDialog(rawIndex, entry) },
                onDeleteClick = { rawIndex -> showDeleteConfirmation(rawIndex) }
            )
        }
    }

    private fun updateMealBreakdown(entries: List<FoodEntry>, mealTargets: Map<String, Int>) {
        val card = findViewById<View>(R.id.cardMealBreakdown)
        if (mealTargets.isEmpty()) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE

        val consumed = entries
            .groupBy { it.mealType.lowercase().ifBlank { "other" } }
            .mapValues { (_, list) -> list.sumOf { it.calories } }

        data class MealIds(val kcalId: Int, val pbId: Int, val ofId: Int)

        val meals = listOf(
            "breakfast" to MealIds(R.id.tvMealBfastKcal,  R.id.pbMealBfast,  R.id.tvMealBfastOf),
            "lunch"     to MealIds(R.id.tvMealLunchKcal,  R.id.pbMealLunch,  R.id.tvMealLunchOf),
            "dinner"    to MealIds(R.id.tvMealDinnerKcal, R.id.pbMealDinner, R.id.tvMealDinnerOf),
            "snack"     to MealIds(R.id.tvMealSnackKcal,  R.id.pbMealSnack,  R.id.tvMealSnackOf)
        )

        for ((mealType, ids) in meals) {
            val eat = consumed[mealType] ?: 0
            val tgt = mealTargets[mealType] ?: 0
            findViewById<TextView>(ids.kcalId).text    = eat.toString()
            findViewById<ProgressBar>(ids.pbId).progress =
                if (tgt > 0) ((eat * 100) / tgt).coerceAtMost(100) else 0
            findViewById<TextView>(ids.ofId).text      = "of $tgt"
        }
    }

    private fun showEditDialog(index: Int, entry: FoodEntry) {
        val view    = layoutInflater.inflate(R.layout.dialog_manual_food, null)
        val tilName = view.findViewById<TextInputLayout>(R.id.tilManualName)
        val tilCal  = view.findViewById<TextInputLayout>(R.id.tilManualCalories)
        val etName  = view.findViewById<TextInputEditText>(R.id.etManualName)
        val etCal   = view.findViewById<TextInputEditText>(R.id.etManualCalories)
        val etPro   = view.findViewById<TextInputEditText>(R.id.etManualProtein)
        val etFat   = view.findViewById<TextInputEditText>(R.id.etManualFat)
        val etCarbs = view.findViewById<TextInputEditText>(R.id.etManualCarbs)

        etName.setText(entry.name)
        etCal.setText(entry.calories.toString())
        etPro.setText(entry.proteinG.toString())
        etFat.setText(entry.fatG.toString())
        etCarbs.setText(entry.carbsG.toString())

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_entry_title)
            .setView(view)
            .setPositiveButton(R.string.btn_save_food) { _, _ ->
                val name   = etName.text.toString().trim()
                val calStr = etCal.text.toString().trim()
                var valid  = true
                if (name.isEmpty())   { tilName.error = getString(R.string.error_required); valid = false } else tilName.error = null
                if (calStr.isEmpty()) { tilCal.error  = getString(R.string.error_required); valid = false } else tilCal.error  = null
                if (!valid) return@setPositiveButton

                val updated = FoodEntry(
                    name     = name,
                    calories = calStr.toIntOrNull()                         ?: 0,
                    proteinG = etPro.text.toString().trim().toIntOrNull()   ?: 0,
                    fatG     = etFat.text.toString().trim().toIntOrNull()   ?: 0,
                    carbsG   = etCarbs.text.toString().trim().toIntOrNull() ?: 0
                )
                updateEntryInLog(index, updated)
                refreshFoodLog()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteConfirmation(index: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_entry_title)
            .setMessage(R.string.delete_entry_message)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                removeEntryFromLog(index)
                refreshFoodLog()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateEntryInLog(index: Int, updated: FoodEntry) {
        val prefs  = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val logKey = "food_log_${todayDateKey()}"
        val arr    = JSONArray(prefs.getString(logKey, "[]") ?: "[]")
        arr.put(index, JSONObject().apply {
            put("name", updated.name); put("cal", updated.calories)
            put("pro",  updated.proteinG); put("fat", updated.fatG); put("car", updated.carbsG)
        })
        prefs.edit().putString(logKey, arr.toString()).apply()
    }

    private fun removeEntryFromLog(index: Int) {
        val prefs  = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val logKey = "food_log_${todayDateKey()}"
        val arr    = JSONArray(prefs.getString(logKey, "[]") ?: "[]")
        arr.remove(index)
        prefs.edit().putString(logKey, arr.toString()).apply()
    }

    // ── Daily notification ───────────────────────────────────────────────────

    private fun scheduleDailySummaryNotification() {
        val prefs        = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent       = Intent(this, DailySummaryReceiver::class.java)
        val pending      = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!prefs.getBoolean(SettingsActivity.PREF_NOTIF_ENABLED, true)) {
            alarmManager.cancel(pending)
            return
        }

        val hour   = prefs.getInt(SettingsActivity.PREF_NOTIF_HOUR,   20)
        val minute = prefs.getInt(SettingsActivity.PREF_NOTIF_MINUTE,  0)
        val triggerTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, triggerTime, AlarmManager.INTERVAL_DAY, pending
        )
    }

    // ── Step sensor ──────────────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return

        val prefs       = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val todayKey    = todayDateKey()
        val baselineKey = "steps_baseline_$todayKey"
        val rawTotal    = event.values[0].toLong()

        if (!prefs.contains(baselineKey)) {
            prefs.edit().putLong(baselineKey, rawTotal).apply()
        }

        val baseline   = prefs.getLong(baselineKey, rawTotal)
        val todaySteps = (rawTotal - baseline).coerceAtLeast(0L).toInt()

        prefs.edit().putInt("steps_$todayKey", todaySteps).apply()
        updateStepUI(todaySteps)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun stepGoal(): Int =
        getSharedPreferences("macromax_prefs", MODE_PRIVATE)
            .getInt(SettingsActivity.PREF_STEP_GOAL, 10_000)

    private fun updateStepUI(steps: Int) {
        val goal         = stepGoal()
        val stepCalories = (steps * 0.04).toInt()
        val totalBurned  = stepCalories + workoutCaloriesToday
        burnedCalToday        = totalBurned
        tvStepCount.text      = steps.toString()
        tvCaloriesBurned.text = "$totalBurned kcal"
        stepDonut.progress    = (steps.toFloat() / goal).coerceIn(0f, 1f)
        stepDonut.centerText  = totalBurned.toString()
        updateNetCaloriesDisplay()
    }

    private fun updateNetCaloriesDisplay() {
        val net = consumedCalToday - burnedCalToday
        if (consumedCalToday == 0 && burnedCalToday == 0) {
            tvNetCalories.visibility = View.GONE
            return
        }
        tvNetCalories.visibility = View.VISIBLE
        tvNetCalories.text = when {
            burnedCalToday > 0 -> "Net ${net} kcal (${consumedCalToday} eaten − ${burnedCalToday} burned)"
            else               -> "${consumedCalToday} kcal consumed"
        }
    }

    // ── Workouts ──────────────────────────────────────────────────────────────

    private fun refreshWorkouts() {
        val prefs    = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val workouts = WorkoutRepository.load(prefs, todayDateKey())
        workoutCaloriesToday = workouts.sumOf { it.caloriesBurned }

        // Re-render step UI so total burned reflects new workout calories
        val steps = prefs.getInt("steps_${todayDateKey()}", 0)
        updateStepUI(steps)

        containerWorkouts.removeAllViews()
        if (workouts.isEmpty()) {
            tvWorkoutsEmpty.visibility   = View.VISIBLE
            containerWorkouts.visibility = View.GONE
        } else {
            tvWorkoutsEmpty.visibility   = View.GONE
            containerWorkouts.visibility = View.VISIBLE
            workouts.forEachIndexed { idx, workout ->
                if (idx > 0) containerWorkouts.addView(dividerView())
                containerWorkouts.addView(buildWorkoutRow(workout))
            }
        }
    }

    private fun buildWorkoutRow(workout: WorkoutEntry): View {
        val dp  = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
        }

        val left = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        left.addView(TextView(this).apply {
            text     = workout.exerciseName
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getColor(android.R.color.transparent).let {
                val ta = obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
                val c  = ta.getColor(0, android.graphics.Color.BLACK); ta.recycle(); c
            })
        })
        left.addView(TextView(this).apply {
            text     = "${workout.durationMinutes} min"
            textSize = 11f
            alpha    = 0.5f
        })

        val tvCal = TextView(this).apply {
            text     = "${workout.caloriesBurned} kcal"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            val ta = obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
            setTextColor(ta.getColor(0, android.graphics.Color.BLACK)); ta.recycle()
        }

        val btnDelete = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            background = null
            alpha      = 0.4f
            setPadding((8 * dp).toInt(), 0, 0, 0)
            setOnClickListener { confirmDeleteWorkout(workout) }
        }

        row.addView(left)
        row.addView(tvCal)
        row.addView(btnDelete)
        return row
    }

    private fun dividerView(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (0.75f * resources.displayMetrics.density).toInt()
        )
        setBackgroundColor(getColor(R.color.divider_line))
    }

    private fun confirmDeleteWorkout(workout: WorkoutEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.workout_delete_title)
            .setMessage(R.string.workout_delete_message)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
                WorkoutRepository.delete(prefs, todayDateKey(), workout.id)
                refreshWorkouts()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun todayDateKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun hasActivityPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestActivityPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasActivityPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACTIVITY_RECOGNITION),
                PERMISSION_REQUEST_ACTIVITY
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_ACTIVITY &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            stepSensor?.also { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }
}
