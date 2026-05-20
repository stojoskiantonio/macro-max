package com.example.macromax

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREF_GEMINI_KEY = "gemini_api_key"

        const val PREF_NOTIF_ENABLED = "notif_daily_enabled"
        const val PREF_NOTIF_HOUR    = "notif_daily_hour"
        const val PREF_NOTIF_MINUTE  = "notif_daily_minute"

        const val PREF_STEP_GOAL     = "step_goal"

        const val PREF_UNITS         = "units_system"
        const val UNITS_METRIC       = "metric"
        const val UNITS_IMPERIAL     = "imperial"

        const val PREF_BUDGET_NOTIF_ENABLED = "budget_notif_enabled"

        const val PREF_WATER_UNIT    = "water_unit"
        const val WATER_UNIT_GLASSES = "glasses"
        const val WATER_UNIT_ML      = "ml"
        const val ML_PER_GLASS       = 250

        const val PREF_MEAL_SPLIT_BREAKFAST = "meal_split_breakfast"
        const val PREF_MEAL_SPLIT_LUNCH     = "meal_split_lunch"
        const val PREF_MEAL_SPLIT_DINNER    = "meal_split_dinner"
        const val PREF_MEAL_SPLIT_SNACK     = "meal_split_snack"
        const val DEFAULT_SPLIT_BREAKFAST   = 25
        const val DEFAULT_SPLIT_LUNCH       = 35
        const val DEFAULT_SPLIT_DINNER      = 30
        const val DEFAULT_SPLIT_SNACK       = 10
    }

    private lateinit var tvNotifTime: TextView
    private lateinit var rowNotifTime: View
    private lateinit var dividerNotifTime: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.btnSettingsBack).setOnClickListener { finish() }

        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)

        // ── Light mode ────────────────────────────────────────────────────────
        val switchLightMode = findViewById<SwitchMaterial>(R.id.switchLightMode)
        switchLightMode.isChecked = prefs.getBoolean("light_mode", false)
        switchLightMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("light_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_NO
                else           AppCompatDelegate.MODE_NIGHT_YES
            )
        }

        // ── Language ──────────────────────────────────────────────────────────
        val toggleLanguage = findViewById<MaterialButtonToggleGroup>(R.id.toggleLanguage)
        val currentLang = AppCompatDelegate.getApplicationLocales()
            .let { if (it.isEmpty) "en" else (it[0]?.language ?: "en") }
        toggleLanguage.check(if (currentLang == "mk") R.id.btnLangMacedonian else R.id.btnLangEnglish)
        toggleLanguage.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val tag = if (checkedId == R.id.btnLangMacedonian) "mk" else "en"
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }

        // ── Notifications ─────────────────────────────────────────────────────
        tvNotifTime      = findViewById(R.id.tvNotifTime)
        rowNotifTime     = findViewById(R.id.rowNotifTime)
        dividerNotifTime = findViewById(R.id.dividerNotifTime)

        val notifEnabled = prefs.getBoolean(PREF_NOTIF_ENABLED, true)
        val switchNotif  = findViewById<SwitchMaterial>(R.id.switchNotifSummary)
        switchNotif.isChecked = notifEnabled
        setNotifTimeRowVisible(notifEnabled)
        updateNotifTimeLabel(
            prefs.getInt(PREF_NOTIF_HOUR, 20),
            prefs.getInt(PREF_NOTIF_MINUTE, 0)
        )

        switchNotif.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_NOTIF_ENABLED, isChecked).apply()
            setNotifTimeRowVisible(isChecked)
            scheduleOrCancelNotification(isChecked)
        }

        // ── Live budget notification ───────────────────────────────────────────
        val switchBudget = findViewById<SwitchMaterial>(R.id.switchBudgetNotif)
        switchBudget.isChecked = prefs.getBoolean(PREF_BUDGET_NOTIF_ENABLED, false)
        switchBudget.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_BUDGET_NOTIF_ENABLED, isChecked).apply()
            if (isChecked) BudgetNotifHelper.refresh(this)
            else           BudgetNotifHelper.cancel(this)
        }

        rowNotifTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, h, m ->
                    prefs.edit().putInt(PREF_NOTIF_HOUR, h).putInt(PREF_NOTIF_MINUTE, m).apply()
                    updateNotifTimeLabel(h, m)
                    scheduleOrCancelNotification(true)
                },
                prefs.getInt(PREF_NOTIF_HOUR, 20),
                prefs.getInt(PREF_NOTIF_MINUTE, 0),
                false // 12-hour clock
            ).show()
        }

        // ── Step goal ─────────────────────────────────────────────────────────
        val tvStepGoal = findViewById<TextView>(R.id.tvStepGoal)
        tvStepGoal.text = "%,d".format(prefs.getInt(PREF_STEP_GOAL, 10_000))

        findViewById<View>(R.id.rowStepGoal).setOnClickListener {
            showStepGoalDialog(prefs.getInt(PREF_STEP_GOAL, 10_000)) { newGoal ->
                prefs.edit().putInt(PREF_STEP_GOAL, newGoal).apply()
                tvStepGoal.text = "%,d".format(newGoal)
            }
        }

        // ── Units ─────────────────────────────────────────────────────────────
        val toggleUnits  = findViewById<MaterialButtonToggleGroup>(R.id.toggleUnits)
        val currentUnits = prefs.getString(PREF_UNITS, UNITS_METRIC) ?: UNITS_METRIC
        toggleUnits.check(if (currentUnits == UNITS_IMPERIAL) R.id.btnImperial else R.id.btnMetric)
        toggleUnits.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val toImperial    = checkedId == R.id.btnImperial
            val newUnitSystem = if (toImperial) UNITS_IMPERIAL else UNITS_METRIC
            val newWeightUnit = if (toImperial) "lbs" else "kg"

            // Convert weight_value if the unit is actually changing
            val oldWeightUnit  = prefs.getString("weight_unit", "kg") ?: "kg"
            val oldWeightValue = prefs.getInt("weight_value", 0)
            val newWeightValue = when {
                oldWeightUnit == newWeightUnit -> oldWeightValue          // no change
                toImperial                     -> (oldWeightValue * 2.205).roundToInt()  // kg → lbs
                else                           -> (oldWeightValue / 2.205).roundToInt()  // lbs → kg
            }

            prefs.edit()
                .putString(PREF_UNITS,      newUnitSystem)
                .putString("weight_unit",   newWeightUnit)
                .putInt("weight_value",     newWeightValue)
                .apply()
        }

        // ── Water unit ────────────────────────────────────────────────────────
        val toggleWaterUnit   = findViewById<MaterialButtonToggleGroup>(R.id.toggleWaterUnit)
        val currentWaterUnit  = prefs.getString(PREF_WATER_UNIT, WATER_UNIT_GLASSES) ?: WATER_UNIT_GLASSES
        toggleWaterUnit.check(if (currentWaterUnit == WATER_UNIT_ML) R.id.btnWaterMl else R.id.btnWaterGlasses)
        toggleWaterUnit.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val unit = if (checkedId == R.id.btnWaterMl) WATER_UNIT_ML else WATER_UNIT_GLASSES
            prefs.edit().putString(PREF_WATER_UNIT, unit).apply()
        }

        // ── Meal targets ──────────────────────────────────────────────────────
        val tvSplitBfast  = findViewById<TextView>(R.id.tvMealSplitBfast)
        val tvSplitLunch  = findViewById<TextView>(R.id.tvMealSplitLunch)
        val tvSplitDinner = findViewById<TextView>(R.id.tvMealSplitDinner)
        val tvSplitSnack  = findViewById<TextView>(R.id.tvMealSplitSnack)
        val tvSplitTotal  = findViewById<TextView>(R.id.tvMealSplitTotal)

        fun refreshSplitUI() {
            val b = prefs.getInt(PREF_MEAL_SPLIT_BREAKFAST, DEFAULT_SPLIT_BREAKFAST)
            val l = prefs.getInt(PREF_MEAL_SPLIT_LUNCH,     DEFAULT_SPLIT_LUNCH)
            val d = prefs.getInt(PREF_MEAL_SPLIT_DINNER,    DEFAULT_SPLIT_DINNER)
            val s = prefs.getInt(PREF_MEAL_SPLIT_SNACK,     DEFAULT_SPLIT_SNACK)
            tvSplitBfast.text  = "%d%%".format(b)
            tvSplitLunch.text  = "%d%%".format(l)
            tvSplitDinner.text = "%d%%".format(d)
            tvSplitSnack.text  = "%d%%".format(s)
            tvSplitTotal.text  = getString(R.string.settings_meal_total, b + l + d + s)
        }
        refreshSplitUI()

        for ((rowId, prefKey, default) in listOf(
            Triple(R.id.rowMealBfast,  PREF_MEAL_SPLIT_BREAKFAST, DEFAULT_SPLIT_BREAKFAST),
            Triple(R.id.rowMealLunch,  PREF_MEAL_SPLIT_LUNCH,     DEFAULT_SPLIT_LUNCH),
            Triple(R.id.rowMealDinner, PREF_MEAL_SPLIT_DINNER,    DEFAULT_SPLIT_DINNER),
            Triple(R.id.rowMealSnack,  PREF_MEAL_SPLIT_SNACK,     DEFAULT_SPLIT_SNACK)
        )) {
            findViewById<View>(rowId).setOnClickListener {
                showMealSplitDialog(prefs.getInt(prefKey, default)) { newPct ->
                    prefs.edit().putInt(prefKey, newPct).apply()
                    refreshSplitUI()
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setNotifTimeRowVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        rowNotifTime.visibility     = v
        dividerNotifTime.visibility = v
    }

    private fun updateNotifTimeLabel(hour: Int, minute: Int) {
        val amPm = if (hour < 12) "AM" else "PM"
        val h12  = when {
            hour == 0  -> 12
            hour > 12  -> hour - 12
            else       -> hour
        }
        tvNotifTime.text = "%d:%02d %s".format(h12, minute, amPm)
    }

    private fun scheduleOrCancelNotification(enable: Boolean) {
        val alarmManager  = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent        = Intent(this, DailySummaryReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (!enable) {
            alarmManager.cancel(pendingIntent)
            return
        }
        val prefs  = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val hour   = prefs.getInt(PREF_NOTIF_HOUR, 20)
        val minute = prefs.getInt(PREF_NOTIF_MINUTE, 0)
        val triggerTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, triggerTime, AlarmManager.INTERVAL_DAY, pendingIntent
        )
    }

    private fun showMealSplitDialog(current: Int, onSave: (Int) -> Unit) {
        val dp   = resources.displayMetrics.density
        val hPad = (24 * dp).toInt()
        val til  = TextInputLayout(
            this, null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            hint = getString(R.string.settings_meal_target_hint)
            setPadding(hPad, 0, hPad, 0)
        }
        val et = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.toString())
            selectAll()
        }
        til.addView(et)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_meal_targets_section)
            .setView(til)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val v = et.text.toString().toIntOrNull()
                if (v != null && v in 1..99) onSave(v)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showStepGoalDialog(current: Int, onSave: (Int) -> Unit) {
        val dp   = resources.displayMetrics.density
        val hPad = (24 * dp).toInt()
        val til  = TextInputLayout(
            this, null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            hint = getString(R.string.settings_step_goal_hint)
            setPadding(hPad, 0, hPad, 0)
        }
        val et = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.toString())
            selectAll()
        }
        til.addView(et)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_step_goal_label)
            .setView(til)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val v = et.text.toString().toIntOrNull()
                if (v != null && v > 0) onSave(v)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
