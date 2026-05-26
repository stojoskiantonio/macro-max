package com.example.macromax

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class WorkoutLogActivity : AppCompatActivity() {

    private lateinit var cgExercise: ChipGroup
    private lateinit var etDuration: TextInputEditText
    private lateinit var tvEstCalories: TextView

    // exercise key → (string res, MET value)
    private val exercises = listOf(
        Triple("running",         R.string.workout_type_running,    8.0f),
        Triple("walking",         R.string.workout_type_walking,    3.5f),
        Triple("cycling",         R.string.workout_type_cycling,    7.0f),
        Triple("swimming",        R.string.workout_type_swimming,   6.0f),
        Triple("weight_training", R.string.workout_type_weights,    3.5f),
        Triple("hiit",            R.string.workout_type_hiit,       8.0f),
        Triple("jump_rope",       R.string.workout_type_jump_rope,  10.0f),
        Triple("yoga",            R.string.workout_type_yoga,       2.5f),
        Triple("other",           R.string.workout_type_other,      4.0f)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_log)

        findViewById<ImageButton>(R.id.btnWorkoutBack).setOnClickListener { finish() }

        cgExercise    = findViewById(R.id.cgExerciseType)
        etDuration    = findViewById(R.id.etWorkoutDuration)
        tvEstCalories = findViewById(R.id.tvEstCalories)

        exercises.forEachIndexed { idx, (key, labelRes, _) ->
            cgExercise.addView(Chip(this).apply {
                id          = idx + 1
                text        = getString(labelRes)
                isCheckable = true
                tag         = key
            })
        }
        cgExercise.check(1) // default: Running

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateEstimate() }
        }
        etDuration.addTextChangedListener(watcher)
        cgExercise.setOnCheckedStateChangeListener { _, _ -> updateEstimate() }
        updateEstimate()

        findViewById<MaterialButton>(R.id.btnSaveWorkout).setOnClickListener { saveWorkout() }
    }

    private fun userWeightKg(): Float {
        val prefs  = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val value  = prefs.getInt("weight_value", 70).takeIf { it > 0 } ?: 70
        val isLbs  = prefs.getString("weight_unit", "kg") == "lbs"
        return if (isLbs) value / 2.205f else value.toFloat()
    }

    private fun selectedMet(): Float {
        val id   = cgExercise.checkedChipIds.firstOrNull() ?: return 4.0f
        val chip = cgExercise.findViewById<Chip>(id) ?: return 4.0f
        return exercises.find { it.first == chip.tag }?.third ?: 4.0f
    }

    private fun estimateCalories(durationMin: Int): Int =
        (selectedMet() * userWeightKg() * durationMin / 60f).toInt()

    private fun updateEstimate() {
        val duration = etDuration.text.toString().toIntOrNull() ?: 0
        if (duration > 0) {
            tvEstCalories.text       = getString(R.string.workout_est_calories, estimateCalories(duration))
            tvEstCalories.visibility = View.VISIBLE
        } else {
            tvEstCalories.visibility = View.INVISIBLE
        }
    }

    private fun saveWorkout() {
        val duration = etDuration.text.toString().toIntOrNull()
        if (duration == null || duration <= 0) {
            etDuration.error = getString(R.string.error_required)
            return
        }

        val checkedId    = cgExercise.checkedChipIds.firstOrNull()
        val chip         = checkedId?.let { cgExercise.findViewById<Chip>(it) }
        val exerciseKey  = chip?.tag as? String ?: "other"
        val exerciseName = chip?.text?.toString() ?: getString(R.string.workout_type_other)

        val prefs   = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val dateKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

        WorkoutRepository.save(
            prefs, dateKey,
            WorkoutEntry(
                id              = UUID.randomUUID().toString(),
                exerciseType    = exerciseKey,
                exerciseName    = exerciseName,
                durationMinutes = duration,
                caloriesBurned  = estimateCalories(duration)
            )
        )
        FirestoreRepository.syncWorkouts(dateKey, prefs)

        Toast.makeText(this, getString(R.string.workout_saved), Toast.LENGTH_SHORT).show()
        finish()
    }
}
