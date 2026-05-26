package com.example.macromax

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class ProfileActivity : AppCompatActivity() {

    private lateinit var ivProfileAvatar: ShapeableImageView
    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileEmail: TextView
    private val avatarFile get() = File(filesDir, "profile_picture.jpg")

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { saveAndDisplayAvatar(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        ivProfileAvatar = findViewById(R.id.ivProfileAvatar)
        tvProfileName   = findViewById(R.id.tvProfileName)
        tvProfileEmail  = findViewById(R.id.tvProfileEmail)

        // Close button
        findViewById<ImageButton>(R.id.btnProfileBack).setOnClickListener { finish() }

        // Avatar tap
        val openPicker = { pickImageLauncher.launch("image/*") }
        ivProfileAvatar.setOnClickListener { openPicker() }
        findViewById<ImageView>(R.id.ivEditBadge).setOnClickListener { openPicker() }

        // Row click listeners
        findViewById<View>(R.id.rowHealthDetails).setOnClickListener   { showHealthDetailsSheet() }
        findViewById<View>(R.id.rowNutritionGoals).setOnClickListener  { showNutritionGoalsSheet() }
        findViewById<View>(R.id.rowUnitsLanguage).setOnClickListener   { showUnitsLanguageSheet() }
        findViewById<View>(R.id.rowWaterGoal).setOnClickListener       { showWaterGoalSheet() }
        findViewById<View>(R.id.rowSettings).setOnClickListener        {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Weight progress
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardWeightProgress)
            .setOnClickListener { startActivity(Intent(this, WeightHistoryActivity::class.java)) }

        // Log out
        findViewById<View>(R.id.btnLogOut).setOnClickListener { confirmLogOut() }

        loadUserInfo()
    }

    override fun onResume() {
        super.onResume()
        loadUserInfo()
    }

    // ── User info ─────────────────────────────────────────────────────────────

    private fun loadUserInfo() {
        val user  = FirebaseAuth.getInstance().currentUser
        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)

        val displayName = when {
            !user?.displayName.isNullOrBlank() -> user!!.displayName!!
            !user?.email.isNullOrBlank()       -> user!!.email!!.substringBefore("@")
            else                               -> prefs.getString("user_name", "") ?: ""
        }
        tvProfileName.text  = displayName
        tvProfileEmail.text = user?.email ?: ""
        displaySavedAvatar()
    }

    // ── Avatar ────────────────────────────────────────────────────────────────

    private fun saveAndDisplayAvatar(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(avatarFile).use { output -> input.copyTo(output) }
            }
            displaySavedAvatar()
        } catch (_: Exception) {}
    }

    private fun displaySavedAvatar() {
        if (avatarFile.exists()) {
            val bmp = BitmapFactory.decodeFile(avatarFile.absolutePath)
            if (bmp != null) {
                ivProfileAvatar.setImageBitmap(bmp)
                ivProfileAvatar.setPadding(0, 0, 0, 0)
                return
            }
        }
    }

    // ── Health Details sheet ──────────────────────────────────────────────────

    private fun showHealthDetailsSheet() {
        val sheet  = BottomSheetDialog(this)
        val view   = layoutInflater.inflate(R.layout.bottom_sheet_health_details, null)
        sheet.setContentView(view)

        val prefs      = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val isImperial = prefs.getString(SettingsActivity.PREF_UNITS, SettingsActivity.UNITS_METRIC) ==
                SettingsActivity.UNITS_IMPERIAL

        val etAge      = view.findViewById<TextInputEditText>(R.id.etHdAge)
        val etWeight   = view.findViewById<TextInputEditText>(R.id.etHdWeight)
        val tilWeight  = view.findViewById<TextInputLayout>(R.id.tilHdWeight)
        val tilMetric  = view.findViewById<TextInputLayout>(R.id.tilHdHeightMetric)
        val etHeightCm = view.findViewById<TextInputEditText>(R.id.etHdHeightCm)
        val rowImp     = view.findViewById<View>(R.id.rowHdHeightImperial)
        val etFt       = view.findViewById<TextInputEditText>(R.id.etHdHeightFt)
        val etIn       = view.findViewById<TextInputEditText>(R.id.etHdHeightIn)
        val rgGender   = view.findViewById<RadioGroup>(R.id.rgHdGender)
        val rbMale     = view.findViewById<RadioButton>(R.id.rbHdMale)
        val rbFemale   = view.findViewById<RadioButton>(R.id.rbHdFemale)
        val rgActivity = view.findViewById<RadioGroup>(R.id.rgHdActivity)
        val rbSed      = view.findViewById<RadioButton>(R.id.rbHdSedentary)
        val rbLight    = view.findViewById<RadioButton>(R.id.rbHdLight)
        val rbMod      = view.findViewById<RadioButton>(R.id.rbHdModerate)
        val rbActive   = view.findViewById<RadioButton>(R.id.rbHdActive)
        val rbExtra    = view.findViewById<RadioButton>(R.id.rbHdExtra)
        val rgGoal     = view.findViewById<RadioGroup>(R.id.rgHdGoal)
        val rbLose     = view.findViewById<RadioButton>(R.id.rbHdLose)
        val rbMaintain = view.findViewById<RadioButton>(R.id.rbHdMaintain)
        val rbGain     = view.findViewById<RadioButton>(R.id.rbHdGain)

        // Pre-fill
        val age       = prefs.getInt("user_age", 0)
        val weightVal = prefs.getInt("weight_value", 0)
        val heightCm  = prefs.getInt("height_cm", 0)
        val gender    = prefs.getString("user_gender", "male") ?: "male"
        val goal      = prefs.getString("user_goal", "maintain") ?: "maintain"
        val activity  = prefs.getString("activity_level", "moderate") ?: "moderate"

        if (age > 0)       etAge.setText(age.toString())
        if (weightVal > 0) etWeight.setText(weightVal.toString())
        tilWeight.hint = getString(R.string.profile_weight) +
                if (isImperial) " (${getString(R.string.weight_lbs)})"
                else            " (${getString(R.string.weight_kg)})"

        if (isImperial) {
            tilMetric.visibility = View.GONE
            rowImp.visibility    = View.VISIBLE
            if (heightCm > 0) {
                val totalInches = (heightCm / 2.54).toInt()
                etFt.setText((totalInches / 12).toString())
                etIn.setText((totalInches % 12).toString())
            }
        } else {
            tilMetric.visibility = View.VISIBLE
            rowImp.visibility    = View.GONE
            if (heightCm > 0) etHeightCm.setText(heightCm.toString())
        }

        if (gender == "female") rbFemale.isChecked = true else rbMale.isChecked = true
        when (activity) {
            "sedentary" -> rbSed.isChecked    = true
            "light"     -> rbLight.isChecked  = true
            "active"    -> rbActive.isChecked = true
            "extra"     -> rbExtra.isChecked  = true
            else        -> rbMod.isChecked    = true
        }
        when (goal) {
            "lose" -> rbLose.isChecked     = true
            "gain" -> rbGain.isChecked     = true
            else   -> rbMaintain.isChecked = true
        }

        view.findViewById<MaterialButton>(R.id.btnHealthDetailsSave).setOnClickListener {
            val ageStr    = etAge.text.toString().trim()
            val weightStr = etWeight.text.toString().trim()
            if (ageStr.isEmpty() || weightStr.isEmpty()) return@setOnClickListener

            val ageVal    = ageStr.toIntOrNull() ?: return@setOnClickListener
            val weightVl  = weightStr.toIntOrNull() ?: return@setOnClickListener
            val heightVal: Int = if (isImperial) {
                val ft  = etFt.text.toString().trim().toIntOrNull() ?: 0
                val ins = etIn.text.toString().trim().toIntOrNull() ?: 0
                ((ft * 12 + ins) * 2.54).roundToInt()
            } else {
                etHeightCm.text.toString().trim().toIntOrNull() ?: return@setOnClickListener
            }

            val genderStr   = if (rbFemale.isChecked) "female" else "male"
            val goalStr     = when { rbLose.isChecked -> "lose"; rbGain.isChecked -> "gain"; else -> "maintain" }
            val activityStr = when {
                rbSed.isChecked    -> "sedentary"
                rbLight.isChecked  -> "light"
                rbActive.isChecked -> "active"
                rbExtra.isChecked  -> "extra"
                else               -> "moderate"
            }

            val weightUnit  = if (isImperial) "lbs" else "kg"
            val weightKg    = if (isImperial) weightVl / 2.205 else weightVl.toDouble()
            val bmr = if (genderStr == "male")
                10 * weightKg + 6.25 * heightVal - 5 * ageVal + 5
            else
                10 * weightKg + 6.25 * heightVal - 5 * ageVal - 161
            val tdee           = bmr * ActivityLevelSelectionActivity.multiplier(activityStr)
            val targetCalories = when (goalStr) {
                "gain" -> tdee + 700; "lose" -> tdee - 400; else -> tdee
            }.roundToInt()

            // Check if custom split is enabled — recalculate macros accordingly
            val useCustom  = prefs.getBoolean("custom_macro_split_enabled", false)
            val proteinG: Int; val fatG: Int; val carbG: Int
            if (useCustom) {
                val p = prefs.getInt("custom_protein_pct", 30)
                val f = prefs.getInt("custom_fat_pct",     25)
                val c = prefs.getInt("custom_carb_pct",    45)
                proteinG = ((targetCalories * p / 100.0) / 4).roundToInt()
                fatG     = ((targetCalories * f / 100.0) / 9).roundToInt()
                carbG    = ((targetCalories * c / 100.0) / 4).roundToInt().coerceAtLeast(0)
            } else {
                proteinG = (weightKg * when (goalStr) { "gain" -> 2.6; "lose" -> 1.8; else -> 1.6 }).roundToInt()
                fatG     = ((targetCalories * 0.25) / 9).roundToInt()
                carbG    = ((targetCalories - proteinG * 4 - fatG * 9) / 4).coerceAtLeast(0)
            }

            prefs.edit().apply {
                putInt("user_age",          ageVal)
                putInt("weight_value",      weightVl)
                putString("weight_unit",    weightUnit)
                putInt("height_cm",         heightVal)
                putString("user_gender",    genderStr)
                putString("user_goal",      goalStr)
                putString("activity_level", activityStr)
                putInt("target_calories",   targetCalories)
                putInt("target_protein_g",  proteinG)
                putInt("target_fat_g",      fatG)
                putInt("target_carbs_g",    carbG)
                apply()
            }
            FirestoreRepository.syncProfile(prefs)
            sheet.dismiss()
            Snackbar.make(findViewById(android.R.id.content), getString(R.string.profile_saved), Snackbar.LENGTH_SHORT).show()
        }

        sheet.show()
    }

    // ── Nutrition Goals sheet ─────────────────────────────────────────────────

    private fun showNutritionGoalsSheet() {
        val sheet = BottomSheetDialog(this)
        val view  = layoutInflater.inflate(R.layout.bottom_sheet_nutrition_goals, null)
        sheet.setContentView(view)

        val prefs       = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val targetCal   = prefs.getInt("target_calories",  0)
        val targetPro   = prefs.getInt("target_protein_g", 0)
        val targetFat   = prefs.getInt("target_fat_g",     0)
        val targetCarb  = prefs.getInt("target_carbs_g",   0)

        val tvTargets   = view.findViewById<TextView>(R.id.tvNgCurrentTargets)
        val tvMacros    = view.findViewById<TextView>(R.id.tvNgCurrentMacros)
        val toggle      = view.findViewById<SwitchMaterial>(R.id.switchNgCustomSplit)
        val rowInputs   = view.findViewById<View>(R.id.rowNgSplitInputs)
        val etProtein   = view.findViewById<TextInputEditText>(R.id.etNgProteinPct)
        val etFat       = view.findViewById<TextInputEditText>(R.id.etNgFatPct)
        val etCarb      = view.findViewById<TextInputEditText>(R.id.etNgCarbPct)
        val tvPreview   = view.findViewById<TextView>(R.id.tvNgSplitPreview)

        if (targetCal > 0) {
            tvTargets.text = "$targetCal kcal / day"
            tvMacros.text  = "P ${targetPro}g  ·  F ${targetFat}g  ·  C ${targetCarb}g"
        } else {
            tvTargets.text = getString(R.string.profile_no_targets)
            tvMacros.text  = ""
        }

        val customEnabled = prefs.getBoolean("custom_macro_split_enabled", false)
        toggle.isChecked     = customEnabled
        rowInputs.visibility = if (customEnabled) View.VISIBLE else View.GONE
        etProtein.setText(prefs.getInt("custom_protein_pct", 30).toString())
        etFat.setText(prefs.getInt("custom_fat_pct",         25).toString())
        etCarb.setText(prefs.getInt("custom_carb_pct",       45).toString())

        fun updatePreview() {
            val p = etProtein.text.toString().toIntOrNull() ?: 0
            val f = etFat.text.toString().toIntOrNull()     ?: 0
            val c = etCarb.text.toString().toIntOrNull()    ?: 0
            val total = p + f + c
            if (total != 100) {
                tvPreview.text = getString(R.string.profile_macro_split_total_warn, total)
                return
            }
            if (targetCal <= 0) { tvPreview.text = ""; return }
            val protG = ((targetCal * p / 100.0) / 4).roundToInt()
            val fatG  = ((targetCal * f / 100.0) / 9).roundToInt()
            val carbG = ((targetCal * c / 100.0) / 4).roundToInt()
            tvPreview.text = getString(R.string.profile_macro_split_preview, protG, fatG, carbG, targetCal)
        }

        toggle.setOnCheckedChangeListener { _, isChecked ->
            rowInputs.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { updatePreview() }
        }
        etProtein.addTextChangedListener(watcher)
        etFat.addTextChangedListener(watcher)
        etCarb.addTextChangedListener(watcher)
        updatePreview()

        view.findViewById<MaterialButton>(R.id.btnNutritionSave).setOnClickListener {
            val useCustom = toggle.isChecked
            val p = etProtein.text.toString().toIntOrNull() ?: 0
            val f = etFat.text.toString().toIntOrNull()     ?: 0
            val c = etCarb.text.toString().toIntOrNull()    ?: 0

            if (useCustom && p + f + c != 100) {
                Snackbar.make(view, getString(R.string.profile_macro_split_total_warn, p + f + c), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().apply {
                putBoolean("custom_macro_split_enabled", useCustom)
                if (useCustom) {
                    putInt("custom_protein_pct", p)
                    putInt("custom_fat_pct",     f)
                    putInt("custom_carb_pct",    c)
                    // Recalculate macro grams from existing calorie target
                    if (targetCal > 0) {
                        putInt("target_protein_g", ((targetCal * p / 100.0) / 4).roundToInt())
                        putInt("target_fat_g",     ((targetCal * f / 100.0) / 9).roundToInt())
                        putInt("target_carbs_g",   ((targetCal * c / 100.0) / 4).roundToInt().coerceAtLeast(0))
                    }
                }
                apply()
            }
            FirestoreRepository.syncProfile(prefs)
            sheet.dismiss()
            Snackbar.make(findViewById(android.R.id.content), getString(R.string.profile_saved), Snackbar.LENGTH_SHORT).show()
        }

        sheet.show()
    }

    // ── Units & Language sheet ────────────────────────────────────────────────

    private fun showUnitsLanguageSheet() {
        val sheet = BottomSheetDialog(this)
        val view  = layoutInflater.inflate(R.layout.bottom_sheet_units_language, null)
        sheet.setContentView(view)

        val prefs    = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val toggleUnits    = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleUlUnits)
        val btnMetric      = view.findViewById<MaterialButton>(R.id.btnUlMetric)
        val btnImperial    = view.findViewById<MaterialButton>(R.id.btnUlImperial)
        val toggleWater    = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleUlWaterUnit)
        val btnGlasses     = view.findViewById<MaterialButton>(R.id.btnUlGlasses)
        val btnMl          = view.findViewById<MaterialButton>(R.id.btnUlMl)
        val toggleLanguage = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleUlLanguage)
        val btnEnglish     = view.findViewById<MaterialButton>(R.id.btnUlEnglish)
        val btnMacedonian  = view.findViewById<MaterialButton>(R.id.btnUlMacedonian)

        // Pre-select current values
        val isImperial = prefs.getString(SettingsActivity.PREF_UNITS, SettingsActivity.UNITS_METRIC) == SettingsActivity.UNITS_IMPERIAL
        toggleUnits.check(if (isImperial) R.id.btnUlImperial else R.id.btnUlMetric)

        val isMl = prefs.getString(SettingsActivity.PREF_WATER_UNIT, SettingsActivity.WATER_UNIT_GLASSES) == SettingsActivity.WATER_UNIT_ML
        toggleWater.check(if (isMl) R.id.btnUlMl else R.id.btnUlGlasses)

        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val langTag = currentLocales.toLanguageTags()
        toggleLanguage.check(if (langTag.startsWith("mk")) R.id.btnUlMacedonian else R.id.btnUlEnglish)

        // Live save on toggle changes
        toggleUnits.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val unit = if (checkedId == R.id.btnUlImperial) SettingsActivity.UNITS_IMPERIAL else SettingsActivity.UNITS_METRIC
            prefs.edit().putString(SettingsActivity.PREF_UNITS, unit).apply()
        }
        toggleWater.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val unit = if (checkedId == R.id.btnUlMl) SettingsActivity.WATER_UNIT_ML else SettingsActivity.WATER_UNIT_GLASSES
            prefs.edit().putString(SettingsActivity.PREF_WATER_UNIT, unit).apply()
        }
        toggleLanguage.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val locale = if (checkedId == R.id.btnUlMacedonian) "mk" else "en"
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(locale))
        }

        sheet.show()
    }

    // ── Water Goal sheet ──────────────────────────────────────────────────────

    private fun showWaterGoalSheet() {
        val sheet = BottomSheetDialog(this)
        val view  = layoutInflater.inflate(R.layout.bottom_sheet_water_goal, null)
        sheet.setContentView(view)

        val prefs  = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val etGoal = view.findViewById<TextInputEditText>(R.id.etWaterGoalValue)
        etGoal.setText(prefs.getInt("water_goal", 8).toString())

        view.findViewById<MaterialButton>(R.id.btnWaterGoalSave).setOnClickListener {
            val goal = etGoal.text.toString().trim().toIntOrNull()?.coerceAtLeast(1) ?: 8
            prefs.edit().putInt("water_goal", goal).apply()
            sheet.dismiss()
            Snackbar.make(findViewById(android.R.id.content), getString(R.string.profile_saved), Snackbar.LENGTH_SHORT).show()
        }

        sheet.show()
    }

    // ── Log out ───────────────────────────────────────────────────────────────

    private fun confirmLogOut() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.profile_logout_confirm)
            .setPositiveButton(R.string.btn_log_out) { _, _ ->
                FirebaseAuth.getInstance().signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
