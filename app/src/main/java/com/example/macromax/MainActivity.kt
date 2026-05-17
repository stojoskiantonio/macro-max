package com.example.macromax

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
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
    private lateinit var tvNoFood: TextView
    private lateinit var rvFoodLog: RecyclerView

    private lateinit var tvWaterCount: TextView
    private lateinit var tvWaterGlasses: TextView
    private lateinit var pbWater: ProgressBar

    private val stepGoal = 10_000

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
        tvNoFood         = findViewById(R.id.tvNoFood)
        rvFoodLog        = findViewById(R.id.rvFoodLog)
        tvStepCount      = findViewById(R.id.tvStepCount)
        tvCaloriesBurned = findViewById(R.id.tvCaloriesBurned)
        stepDonut        = findViewById(R.id.stepDonut)
        tvWaterCount     = findViewById(R.id.tvWaterCount)
        tvWaterGlasses   = findViewById(R.id.tvWaterGlasses)
        pbWater          = findViewById(R.id.pbWater)

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

        // Food list — grouped by meal type
        if (entries.isEmpty()) {
            tvNoFood.visibility  = View.VISIBLE
            rvFoodLog.visibility = View.GONE
        } else {
            tvNoFood.visibility  = View.GONE
            rvFoodLog.visibility = View.VISIBLE
            rvFoodLog.adapter    = FoodLogAdapter(
                items         = FoodLogAdapter.buildItems(entries),
                onEditClick   = { rawIndex, entry -> showEditDialog(rawIndex, entry) },
                onDeleteClick = { rawIndex -> showDeleteConfirmation(rawIndex) }
            )
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
        val alarmManager  = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent        = Intent(this, DailySummaryReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Fire at 8 PM today; if already past, fire tomorrow
        val triggerTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
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

    private fun updateStepUI(steps: Int) {
        val burned = (steps * 0.04).toInt()
        tvStepCount.text      = steps.toString()
        tvCaloriesBurned.text = "$burned kcal"
        stepDonut.progress    = (steps.toFloat() / stepGoal).coerceIn(0f, 1f)
        stepDonut.centerText  = burned.toString()
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
