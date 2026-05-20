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
import com.google.android.material.button.MaterialButton
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
    private lateinit var etProfileAge: TextInputEditText
    private lateinit var etProfileWeight: TextInputEditText
    private lateinit var tilProfileWeight: TextInputLayout
    private lateinit var etProfileHeight: TextInputEditText
    private lateinit var tilHeightMetric: TextInputLayout
    private lateinit var rowHeightImperial: View
    private lateinit var etProfileHeightFt: TextInputEditText
    private lateinit var etProfileHeightIn: TextInputEditText
    private lateinit var rgGender: RadioGroup
    private lateinit var rbMale: RadioButton
    private lateinit var rbFemale: RadioButton
    private lateinit var rgGoal: RadioGroup
    private lateinit var rbLose: RadioButton
    private lateinit var rbMaintain: RadioButton
    private lateinit var rbGain: RadioButton
    private lateinit var rgActivity: RadioGroup
    private lateinit var rbSedentary: RadioButton
    private lateinit var rbLight: RadioButton
    private lateinit var rbModerate: RadioButton
    private lateinit var rbActive: RadioButton
    private lateinit var rbExtra: RadioButton
    private lateinit var etWaterGoal: TextInputEditText
    private lateinit var tvCurrentTargets: TextView
    private lateinit var tvCurrentMacros: TextView
    private lateinit var btnSaveProfile: MaterialButton

    // Custom macro split
    private lateinit var switchCustomMacroSplit: SwitchMaterial
    private lateinit var rowMacroSplitInputs: View
    private lateinit var etMacroProteinPct: TextInputEditText
    private lateinit var etMacroFatPct: TextInputEditText
    private lateinit var etMacroCarbPct: TextInputEditText
    private lateinit var tvMacroSplitPreview: TextView

    private lateinit var tvRemovePhoto: TextView
    private val avatarFile get() = File(filesDir, "profile_picture.jpg")

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { saveAndDisplayAvatar(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        findViewById<ImageButton>(R.id.btnProfileBack).setOnClickListener { finish() }

        // Bind views
        ivProfileAvatar      = findViewById(R.id.ivProfileAvatar)
        tvProfileName        = findViewById(R.id.tvProfileName)
        etProfileAge         = findViewById(R.id.etProfileAge)
        etProfileWeight      = findViewById(R.id.etProfileWeight)
        tilProfileWeight     = findViewById(R.id.tilProfileWeight)
        etProfileHeight      = findViewById(R.id.etProfileHeight)
        tilHeightMetric      = findViewById(R.id.tilHeightMetric)
        rowHeightImperial    = findViewById(R.id.rowHeightImperial)
        etProfileHeightFt    = findViewById(R.id.etProfileHeightFt)
        etProfileHeightIn    = findViewById(R.id.etProfileHeightIn)
        rgGender             = findViewById(R.id.rgGender)
        rbMale               = findViewById(R.id.rbMale)
        rbFemale             = findViewById(R.id.rbFemale)
        rgGoal               = findViewById(R.id.rgGoal)
        rbLose               = findViewById(R.id.rbLose)
        rbMaintain           = findViewById(R.id.rbMaintain)
        rbGain               = findViewById(R.id.rbGain)
        rgActivity           = findViewById(R.id.rgActivity)
        rbSedentary          = findViewById(R.id.rbSedentary)
        rbLight              = findViewById(R.id.rbLight)
        rbModerate           = findViewById(R.id.rbModerate)
        rbActive             = findViewById(R.id.rbActive)
        rbExtra              = findViewById(R.id.rbExtra)
        etWaterGoal          = findViewById(R.id.etWaterGoal)
        tvCurrentTargets     = findViewById(R.id.tvCurrentTargets)
        tvCurrentMacros      = findViewById(R.id.tvCurrentMacros)
        btnSaveProfile       = findViewById(R.id.btnSaveProfile)
        switchCustomMacroSplit = findViewById(R.id.switchCustomMacroSplit)
        rowMacroSplitInputs  = findViewById(R.id.rowMacroSplitInputs)
        etMacroProteinPct    = findViewById(R.id.etMacroProteinPct)
        etMacroFatPct        = findViewById(R.id.etMacroFatPct)
        etMacroCarbPct       = findViewById(R.id.etMacroCarbPct)
        tvMacroSplitPreview  = findViewById(R.id.tvMacroSplitPreview)

        // Toggle visibility
        switchCustomMacroSplit.setOnCheckedChangeListener { _, isChecked ->
            rowMacroSplitInputs.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Live preview as user types
        val splitWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateMacroSplitPreview() }
        }
        etMacroProteinPct.addTextChangedListener(splitWatcher)
        etMacroFatPct.addTextChangedListener(splitWatcher)
        etMacroCarbPct.addTextChangedListener(splitWatcher)

        tvRemovePhoto = findViewById(R.id.tvRemovePhoto)

        val openPicker = { pickImageLauncher.launch("image/*") }
        ivProfileAvatar.setOnClickListener { openPicker() }
        findViewById<ImageView>(R.id.ivEditBadge).setOnClickListener { openPicker() }

        tvRemovePhoto.setOnClickListener {
            avatarFile.delete()
            ivProfileAvatar.setImageResource(R.drawable.ic_person)
            ivProfileAvatar.setPadding(
                (18 * resources.displayMetrics.density).toInt(),
                (18 * resources.displayMetrics.density).toInt(),
                (18 * resources.displayMetrics.density).toInt(),
                (18 * resources.displayMetrics.density).toInt()
            )
            tvRemovePhoto.visibility = View.GONE
        }

        loadProfile()

        btnSaveProfile.setOnClickListener { saveProfile() }

        // Weight progress shortcut
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardWeightProgress)
            .setOnClickListener {
                startActivity(Intent(this, WeightHistoryActivity::class.java))
            }

        // Logout
        findViewById<MaterialButton>(R.id.btnLogOut).setOnClickListener {
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

    // ── Avatar ────────────────────────────────────────────────────────────────

    private fun saveAndDisplayAvatar(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(avatarFile).use { output ->
                    input.copyTo(output)
                }
            }
            displaySavedAvatar()
        } catch (e: Exception) {
            Snackbar.make(btnSaveProfile, getString(R.string.error_required), Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun displaySavedAvatar() {
        if (avatarFile.exists()) {
            val bmp = BitmapFactory.decodeFile(avatarFile.absolutePath)
            if (bmp != null) {
                ivProfileAvatar.setImageBitmap(bmp)
                ivProfileAvatar.setPadding(0, 0, 0, 0)
                tvRemovePhoto.visibility = View.VISIBLE
                return
            }
        }
        tvRemovePhoto.visibility = View.GONE
    }

    // ── Load / Save profile ──────────────────────────────────────────────────

    private fun loadProfile() {
        val user  = FirebaseAuth.getInstance().currentUser
        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)

        val displayName = when {
            !user?.displayName.isNullOrBlank() -> user!!.displayName!!
            !user?.email.isNullOrBlank()       -> user!!.email!!.substringBefore("@")
            else                               -> prefs.getString("user_name", "") ?: ""
        }
        tvProfileName.text = displayName
        displaySavedAvatar()

        val age       = prefs.getInt("user_age", 0)
        val weightVal = prefs.getInt("weight_value", 0)
        val heightCm  = prefs.getInt("height_cm", 0)
        val gender    = prefs.getString("user_gender", "male")    ?: "male"
        val goal      = prefs.getString("user_goal", "maintain")  ?: "maintain"

        val isImperial = prefs.getString(SettingsActivity.PREF_UNITS, SettingsActivity.UNITS_METRIC) ==
                SettingsActivity.UNITS_IMPERIAL

        // Weight field
        if (age > 0)       etProfileAge.setText(age.toString())
        if (weightVal > 0) etProfileWeight.setText(weightVal.toString())
        tilProfileWeight.hint = getString(R.string.profile_weight) +
                if (isImperial) " (${getString(R.string.weight_lbs)})"
                else            " (${getString(R.string.weight_kg)})"

        // Height field(s)
        if (isImperial) {
            tilHeightMetric.visibility   = View.GONE
            rowHeightImperial.visibility = View.VISIBLE
            if (heightCm > 0) {
                val totalInches = (heightCm / 2.54).toInt()
                etProfileHeightFt.setText((totalInches / 12).toString())
                etProfileHeightIn.setText((totalInches % 12).toString())
            }
        } else {
            tilHeightMetric.visibility   = View.VISIBLE
            rowHeightImperial.visibility = View.GONE
            if (heightCm > 0) etProfileHeight.setText(heightCm.toString())
        }

        val waterGoal = prefs.getInt("water_goal", 8)
        etWaterGoal.setText(waterGoal.toString())

        // Gender
        if (gender == "female") rbFemale.isChecked = true else rbMale.isChecked = true

        // Goal
        when (goal) {
            "lose" -> rbLose.isChecked = true
            "gain" -> rbGain.isChecked = true
            else   -> rbMaintain.isChecked = true
        }

        // Activity level
        val activityLevel = prefs.getString("activity_level", "moderate") ?: "moderate"
        when (activityLevel) {
            "sedentary" -> rbSedentary.isChecked = true
            "light"     -> rbLight.isChecked     = true
            "active"    -> rbActive.isChecked    = true
            "extra"     -> rbExtra.isChecked     = true
            else        -> rbModerate.isChecked  = true
        }

        // Current targets
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

        // Custom macro split
        val customSplitEnabled = prefs.getBoolean("custom_macro_split_enabled", false)
        switchCustomMacroSplit.isChecked = customSplitEnabled
        rowMacroSplitInputs.visibility   = if (customSplitEnabled) View.VISIBLE else View.GONE
        etMacroProteinPct.setText(prefs.getInt("custom_protein_pct", 30).toString())
        etMacroFatPct.setText(prefs.getInt("custom_fat_pct",     25).toString())
        etMacroCarbPct.setText(prefs.getInt("custom_carb_pct",   45).toString())
        updateMacroSplitPreview()
    }

    private fun updateMacroSplitPreview() {
        val prefs     = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val targetCal = prefs.getInt("target_calories", 0)
        val p = etMacroProteinPct.text.toString().toIntOrNull() ?: 0
        val f = etMacroFatPct.text.toString().toIntOrNull()     ?: 0
        val c = etMacroCarbPct.text.toString().toIntOrNull()    ?: 0
        val total = p + f + c

        if (total != 100) {
            tvMacroSplitPreview.text = getString(R.string.profile_macro_split_total_warn, total)
            return
        }
        if (targetCal <= 0) {
            tvMacroSplitPreview.text = ""
            return
        }
        val protG = ((targetCal * p / 100.0) / 4).roundToInt()
        val fatG  = ((targetCal * f / 100.0) / 9).roundToInt()
        val carbG = ((targetCal * c / 100.0) / 4).roundToInt()
        tvMacroSplitPreview.text = getString(R.string.profile_macro_split_preview,
            protG, fatG, carbG, targetCal)
    }

    private fun saveProfile() {
        val ageStr    = etProfileAge.text.toString().trim()
        val weightStr = etProfileWeight.text.toString().trim()

        val prefs      = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val isImperial = prefs.getString(SettingsActivity.PREF_UNITS, SettingsActivity.UNITS_METRIC) ==
                SettingsActivity.UNITS_IMPERIAL
        val weightUnit = if (isImperial) "lbs" else "kg"

        // Validate
        var valid = true
        if (ageStr.isEmpty())    { etProfileAge.error    = getString(R.string.error_required); valid = false }
        if (weightStr.isEmpty()) { etProfileWeight.error = getString(R.string.error_required); valid = false }
        if (isImperial) {
            if (etProfileHeightFt.text.toString().trim().isEmpty()) {
                etProfileHeightFt.error = getString(R.string.error_required); valid = false
            }
        } else {
            if (etProfileHeight.text.toString().trim().isEmpty()) {
                etProfileHeight.error = getString(R.string.error_required); valid = false
            }
        }
        if (!valid) return

        val age       = ageStr.toIntOrNull()   ?: return
        val weightVal = weightStr.toIntOrNull() ?: return

        // Height: convert ft+in → cm for storage (always stored as cm internally)
        val heightCm: Int = if (isImperial) {
            val ft  = etProfileHeightFt.text.toString().trim().toIntOrNull() ?: 0
            val ins = etProfileHeightIn.text.toString().trim().toIntOrNull() ?: 0
            ((ft * 12 + ins) * 2.54).roundToInt()
        } else {
            etProfileHeight.text.toString().trim().toIntOrNull() ?: return
        }

        val gender = if (rbFemale.isChecked) "female" else "male"
        val goal = when {
            rbLose.isChecked -> "lose"
            rbGain.isChecked -> "gain"
            else             -> "maintain"
        }
        val activityLevel = when {
            rbSedentary.isChecked -> "sedentary"
            rbLight.isChecked     -> "light"
            rbActive.isChecked    -> "active"
            rbExtra.isChecked     -> "extra"
            else                  -> "moderate"
        }

        // Recalculate targets (Mifflin-St Jeor)
        val weightKg = if (weightUnit == "lbs") weightVal / 2.205 else weightVal.toDouble()
        val bmr = if (gender == "male") {
            10 * weightKg + 6.25 * heightCm - 5 * age + 5
        } else {
            10 * weightKg + 6.25 * heightCm - 5 * age - 161
        }
        val tdee = bmr * ActivityLevelSelectionActivity.multiplier(activityLevel)
        val targetCalories = when (goal) {
            "gain" -> tdee + 700
            "lose" -> tdee - 400
            else   -> tdee
        }.roundToInt()
        // Determine final macro grams — custom split OR auto
        val useCustomSplit = switchCustomMacroSplit.isChecked
        val proteinG: Int
        val fatG: Int
        val carbG: Int

        if (useCustomSplit) {
            val p = etMacroProteinPct.text.toString().toIntOrNull() ?: 0
            val f = etMacroFatPct.text.toString().toIntOrNull()     ?: 0
            val c = etMacroCarbPct.text.toString().toIntOrNull()    ?: 0
            if (p + f + c != 100) {
                Snackbar.make(btnSaveProfile,
                    getString(R.string.profile_macro_split_total_warn, p + f + c),
                    Snackbar.LENGTH_SHORT).show()
                return
            }
            proteinG = ((targetCalories * p / 100.0) / 4).roundToInt()
            fatG     = ((targetCalories * f / 100.0) / 9).roundToInt()
            carbG    = ((targetCalories * c / 100.0) / 4).roundToInt().coerceAtLeast(0)
        } else {
            proteinG = (weightKg * when (goal) {
                "gain" -> 2.6; "lose" -> 1.8; else -> 1.6
            }).roundToInt()
            fatG  = ((targetCalories * 0.25) / 9).roundToInt()
            carbG = ((targetCalories - proteinG * 4 - fatG * 9) / 4).coerceAtLeast(0)
        }

        val waterGoal = etWaterGoal.text.toString().trim().toIntOrNull()?.coerceAtLeast(1) ?: 8

        // Save to prefs
        prefs.edit().apply {
            putInt("water_goal",        waterGoal)
            putInt("user_age",          age)
            putInt("weight_value",      weightVal)
            putString("weight_unit",    weightUnit)
            putInt("height_cm",         heightCm)
            putString("user_gender",    gender)
            putString("user_goal",      goal)
            putString("activity_level", activityLevel)
            putInt("target_calories",   targetCalories)
            putInt("target_protein_g",  proteinG)
            putInt("target_fat_g",      fatG)
            putInt("target_carbs_g",    carbG)
            putBoolean("custom_macro_split_enabled", useCustomSplit)
            if (useCustomSplit) {
                putInt("custom_protein_pct", etMacroProteinPct.text.toString().toIntOrNull() ?: 30)
                putInt("custom_fat_pct",     etMacroFatPct.text.toString().toIntOrNull()     ?: 25)
                putInt("custom_carb_pct",    etMacroCarbPct.text.toString().toIntOrNull()    ?: 45)
            }
            apply()
        }

        tvCurrentTargets.text = "$targetCalories kcal / day"
        tvCurrentMacros.text  = "P ${proteinG}g  ·  F ${fatG}g  ·  C ${carbG}g"

        Snackbar.make(btnSaveProfile, getString(R.string.profile_saved), Snackbar.LENGTH_SHORT).show()
    }
}
