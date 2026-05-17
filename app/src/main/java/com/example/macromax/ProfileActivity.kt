package com.example.macromax

import android.os.Bundle
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.roundToInt

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvProfileName: TextView
    private lateinit var etProfileAge: TextInputEditText
    private lateinit var etProfileWeight: TextInputEditText
    private lateinit var etProfileHeight: TextInputEditText
    private lateinit var rgWeightUnit: RadioGroup
    private lateinit var rbKg: RadioButton
    private lateinit var rbLbs: RadioButton
    private lateinit var rgGender: RadioGroup
    private lateinit var rbMale: RadioButton
    private lateinit var rbFemale: RadioButton
    private lateinit var rgGoal: RadioGroup
    private lateinit var rbLose: RadioButton
    private lateinit var rbMaintain: RadioButton
    private lateinit var rbGain: RadioButton
    private lateinit var tvCurrentTargets: TextView
    private lateinit var tvCurrentMacros: TextView
    private lateinit var btnSaveProfile: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Back button
        findViewById<ImageButton>(R.id.btnProfileBack).setOnClickListener { finish() }

        // Bind views
        tvProfileName    = findViewById(R.id.tvProfileName)
        etProfileAge     = findViewById(R.id.etProfileAge)
        etProfileWeight  = findViewById(R.id.etProfileWeight)
        etProfileHeight  = findViewById(R.id.etProfileHeight)
        rgWeightUnit     = findViewById(R.id.rgWeightUnit)
        rbKg             = findViewById(R.id.rbKg)
        rbLbs            = findViewById(R.id.rbLbs)
        rgGender         = findViewById(R.id.rgGender)
        rbMale           = findViewById(R.id.rbMale)
        rbFemale         = findViewById(R.id.rbFemale)
        rgGoal           = findViewById(R.id.rgGoal)
        rbLose           = findViewById(R.id.rbLose)
        rbMaintain       = findViewById(R.id.rbMaintain)
        rbGain           = findViewById(R.id.rbGain)
        tvCurrentTargets = findViewById(R.id.tvCurrentTargets)
        tvCurrentMacros  = findViewById(R.id.tvCurrentMacros)
        btnSaveProfile   = findViewById(R.id.btnSaveProfile)

        loadProfile()

        btnSaveProfile.setOnClickListener { saveProfile() }
    }

    private fun loadProfile() {
        // Display name: Firebase user display name, or stored guest name, or email prefix
        val user = FirebaseAuth.getInstance().currentUser
        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val displayName = when {
            !user?.displayName.isNullOrBlank() -> user!!.displayName!!
            !user?.email.isNullOrBlank()       -> user!!.email!!.substringBefore("@")
            else                               -> prefs.getString("user_name", "") ?: ""
        }
        tvProfileName.text = displayName

        // Pre-fill fields from prefs
        val age        = prefs.getInt("user_age", 0)
        val weightVal  = prefs.getFloat("weight_value", 0f)
        val weightUnit = prefs.getString("weight_unit", "kg") ?: "kg"
        val heightCm   = prefs.getInt("height_cm", 0)
        val gender     = prefs.getString("user_gender", "male") ?: "male"
        val goal       = prefs.getString("user_goal", "maintain") ?: "maintain"

        if (age > 0)          etProfileAge.setText(age.toString())
        if (weightVal > 0f)   etProfileWeight.setText(weightVal.let {
            if (it == it.toLong().toFloat()) it.toLong().toString() else it.toString()
        })
        if (heightCm > 0)     etProfileHeight.setText(heightCm.toString())

        // Weight unit
        if (weightUnit == "lbs") rbLbs.isChecked = true else rbKg.isChecked = true

        // Gender
        if (gender == "female") rbFemale.isChecked = true else rbMale.isChecked = true

        // Goal
        when (goal) {
            "lose"     -> rbLose.isChecked = true
            "gain"     -> rbGain.isChecked = true
            else       -> rbMaintain.isChecked = true
        }

        // Show current targets
        val targetCal  = prefs.getInt("target_calories",  0)
        val targetPro  = prefs.getInt("target_protein_g", 0)
        val targetFat  = prefs.getInt("target_fat_g",     0)
        val targetCarb = prefs.getInt("target_carbs_g",   0)

        if (targetCal > 0) {
            tvCurrentTargets.text = "$targetCal kcal / day"
            tvCurrentMacros.text  = "P ${targetPro}g  ·  F ${targetFat}g  ·  C ${targetCarb}g"
        } else {
            tvCurrentTargets.text = getString(R.string.profile_no_targets)
            tvCurrentMacros.text  = ""
        }
    }

    private fun saveProfile() {
        val ageStr    = etProfileAge.text.toString().trim()
        val weightStr = etProfileWeight.text.toString().trim()
        val heightStr = etProfileHeight.text.toString().trim()

        // Validate
        var valid = true
        if (ageStr.isEmpty())    { etProfileAge.error    = getString(R.string.error_required); valid = false }
        if (weightStr.isEmpty()) { etProfileWeight.error = getString(R.string.error_required); valid = false }
        if (heightStr.isEmpty()) { etProfileHeight.error = getString(R.string.error_required); valid = false }
        if (!valid) return

        val age       = ageStr.toIntOrNull()    ?: return
        val weightVal = weightStr.toFloatOrNull() ?: return
        val heightCm  = heightStr.toIntOrNull()  ?: return

        val weightUnit = if (rbLbs.isChecked) "lbs" else "kg"
        val gender     = if (rbFemale.isChecked) "female" else "male"
        val goal = when {
            rbLose.isChecked     -> "lose"
            rbGain.isChecked     -> "gain"
            else                 -> "maintain"
        }

        // Recalculate targets (Mifflin-St Jeor)
        val weightKg = if (weightUnit == "lbs") weightVal / 2.205 else weightVal.toDouble()
        val bmr = if (gender == "male") {
            10 * weightKg + 6.25 * heightCm - 5 * age + 5
        } else {
            10 * weightKg + 6.25 * heightCm - 5 * age - 161
        }
        val tdee           = bmr * 1.5
        val targetCalories = when (goal) {
            "gain" -> tdee + 700
            "lose" -> tdee - 400
            else   -> tdee
        }.roundToInt()
        val proteinG = (weightKg * when (goal) {
            "gain" -> 2.6; "lose" -> 1.8; else -> 1.6
        }).roundToInt()
        val fatG  = ((targetCalories * 0.25) / 9).roundToInt()
        val carbG = ((targetCalories - proteinG * 4 - fatG * 9) / 4).coerceAtLeast(0)

        // Save to prefs
        getSharedPreferences("macromax_prefs", MODE_PRIVATE).edit().apply {
            putInt("user_age",         age)
            putFloat("weight_value",   weightVal)
            putString("weight_unit",   weightUnit)
            putInt("height_cm",        heightCm)
            putString("user_gender",   gender)
            putString("user_goal",     goal)
            putInt("target_calories",  targetCalories)
            putInt("target_protein_g", proteinG)
            putInt("target_fat_g",     fatG)
            putInt("target_carbs_g",   carbG)
            apply()
        }

        // Refresh displayed targets
        tvCurrentTargets.text = "$targetCalories kcal / day"
        tvCurrentMacros.text  = "P ${proteinG}g  ·  F ${fatG}g  ·  C ${carbG}g"

        Snackbar.make(btnSaveProfile, getString(R.string.profile_saved), Snackbar.LENGTH_SHORT).show()
    }
}
