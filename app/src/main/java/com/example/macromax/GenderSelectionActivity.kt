package com.example.macromax

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class GenderSelectionActivity : AppCompatActivity() {

    private var selectedGender: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        if (prefs.getString("user_gender", null) != null) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_gender_selection)

        val cardMale = findViewById<MaterialCardView>(R.id.cardMale)
        val cardFemale = findViewById<MaterialCardView>(R.id.cardFemale)
        val btnNext = findViewById<MaterialButton>(R.id.btnNext)

        btnNext.isEnabled = false

        cardMale.setOnClickListener { select("male", cardMale, cardFemale, btnNext) }
        cardFemale.setOnClickListener { select("female", cardFemale, cardMale, btnNext) }

        btnNext.setOnClickListener {
            prefs.edit().putString("user_gender", selectedGender).apply()
            goToMain()
        }
    }

    private fun select(gender: String, selected: MaterialCardView, other: MaterialCardView, btnNext: MaterialButton) {
        selectedGender = gender

        // colorPrimary is a platform attribute available since API 21
        val primary = themeColor(android.R.attr.colorPrimary)

        selected.strokeColor = primary
        selected.strokeWidth = dp(2)
        selected.setCardBackgroundColor(ColorUtils.setAlphaComponent(primary, 40))

        other.strokeColor = Color.parseColor("#44FFFFFF")
        other.strokeWidth = dp(1)
        other.setCardBackgroundColor(Color.TRANSPARENT)

        btnNext.isEnabled = true
    }

    private fun themeColor(@AttrRes attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    private fun goToMain() {
        startActivity(Intent(this, GoalSelectionActivity::class.java))
        finish()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
