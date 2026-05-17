package com.example.macromax

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

class MacroResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_complete", false)) {
            goToMain(); return
        }

        setContentView(R.layout.activity_macro_result)

        val age = prefs.getInt("user_age", 25)
        val weightValue = prefs.getInt("weight_value", 70)
        val weightUnit = prefs.getString("weight_unit", "kg")
        val heightCm = prefs.getInt("height_cm", 170)
        val gender = prefs.getString("user_gender", "male")
        val goal = prefs.getString("user_goal", "maintain")

        val weightKg = if (weightUnit == "lbs") weightValue / 2.205 else weightValue.toDouble()

        // Mifflin-St Jeor BMR
        val bmr = if (gender == "male") {
            10 * weightKg + 6.25 * heightCm - 5 * age + 5
        } else {
            10 * weightKg + 6.25 * heightCm - 5 * age - 161
        }

        // TDEE — use saved activity level multiplier
        val activityLevel = prefs.getString("activity_level", "moderate") ?: "moderate"
        val tdee = bmr * ActivityLevelSelectionActivity.multiplier(activityLevel)

        val targetCalories = when (goal) {
            "gain" -> tdee + 700
            "lose" -> tdee - 400
            else -> tdee
        }.roundToInt()

        // Protein targets
        val proteinMultiplier = when (goal) { "gain" -> 2.6; "lose" -> 1.8; else -> 1.6 }
        val proteinG = (weightKg * proteinMultiplier).roundToInt()
        val fatG = ((targetCalories * 0.25) / 9).roundToInt()
        val carbG = (targetCalories - proteinG * 4 - fatG * 9) / 4

        val proteinPct = ((proteinG * 4.0 / targetCalories) * 100).roundToInt()
        val fatPct = ((fatG * 9.0 / targetCalories) * 100).roundToInt()
        val carbPct = 100 - proteinPct - fatPct

        // Save targets
        prefs.edit()
            .putInt("target_calories", targetCalories)
            .putInt("target_protein_g", proteinG)
            .putInt("target_carbs_g", carbG)
            .putInt("target_fat_g", fatG)
            .apply()

        // Set loading title based on goal
        val loadingTitle = when (goal) {
            "gain" -> getString(R.string.result_loading_title_gain)
            "lose" -> getString(R.string.result_loading_title_lose)
            else  -> getString(R.string.result_loading_title_maintain)
        }
        findViewById<TextView>(R.id.tvLoadingTitle).text = loadingTitle

        // Populate result views (hidden for now)
        val goalLabel = when (goal) {
            "gain" -> getString(R.string.goal_gain)
            "lose" -> getString(R.string.goal_lose)
            else -> getString(R.string.goal_maintain)
        }
        findViewById<TextView>(R.id.tvGoalLabel).text = goalLabel
        findViewById<TextView>(R.id.tvCalories).text = targetCalories.toString()
        findViewById<TextView>(R.id.tvProtein).text = getString(R.string.result_grams, proteinG)
        findViewById<TextView>(R.id.tvCarbs).text = getString(R.string.result_grams, carbG)
        findViewById<TextView>(R.id.tvFat).text = getString(R.string.result_grams, fatG)
        findViewById<TextView>(R.id.tvProteinPct).text = "$proteinPct%"
        findViewById<TextView>(R.id.tvCarbsPct).text = "$carbPct%"
        findViewById<TextView>(R.id.tvFatPct).text = "$fatPct%"

        findViewById<MaterialButton>(R.id.btnLetsGo).setOnClickListener {
            prefs.edit().putBoolean("onboarding_complete", true).apply()
            goToMain()
        }

        // Cycle through status messages, then cross-fade to results
        val loadingLayout = findViewById<LinearLayout>(R.id.loadingLayout)
        val scrollResult  = findViewById<View>(R.id.scrollResult)
        val tvStatus      = findViewById<TextView>(R.id.tvLoadingStatus)
        val handler       = Handler(Looper.getMainLooper())

        val statusMessages = listOf(
            getString(R.string.result_loading_status_1),
            getString(R.string.result_loading_status_2),
            getString(R.string.result_loading_status_3),
            getString(R.string.result_loading_status_4)
        )

        fun cycleMessage(index: Int) {
            if (index >= statusMessages.size) return
            tvStatus.animate().alpha(0f).setDuration(300).withEndAction {
                tvStatus.text = statusMessages[index]
                tvStatus.animate().alpha(1f).setDuration(400)
            }
            if (index + 1 < statusMessages.size) {
                handler.postDelayed({ cycleMessage(index + 1) }, 1500)
            }
        }

        // Show first message immediately, then cycle every 1.5 s
        tvStatus.alpha = 0f
        tvStatus.text = statusMessages[0]
        tvStatus.animate().alpha(1f).setDuration(400)
        handler.postDelayed({ cycleMessage(1) }, 1500)

        // Fade to results after ~6.5 s total
        handler.postDelayed({
            loadingLayout.animate()
                .alpha(0f)
                .setDuration(500)
                .withEndAction {
                    loadingLayout.visibility = View.GONE
                    scrollResult.alpha = 0f
                    scrollResult.visibility = View.VISIBLE
                    scrollResult.animate().alpha(1f).setDuration(600)
                }
        }, 6500)
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
