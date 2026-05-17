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

class ActivityLevelSelectionActivity : AppCompatActivity() {

    private var selectedLevel: String? = null
    private val cards = mutableListOf<Pair<MaterialCardView, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        if (prefs.getString("activity_level", null) != null) {
            goToNext()
            return
        }

        setContentView(R.layout.activity_activity_level_selection)

        val cardSedentary = findViewById<MaterialCardView>(R.id.cardSedentary)
        val cardLight     = findViewById<MaterialCardView>(R.id.cardLight)
        val cardModerate  = findViewById<MaterialCardView>(R.id.cardModerate)
        val cardActive    = findViewById<MaterialCardView>(R.id.cardActive)
        val cardExtra     = findViewById<MaterialCardView>(R.id.cardExtra)
        val btnNext       = findViewById<MaterialButton>(R.id.btnNext)

        cards.addAll(listOf(
            cardSedentary to "sedentary",
            cardLight     to "light",
            cardModerate  to "moderate",
            cardActive    to "active",
            cardExtra     to "extra"
        ))

        btnNext.isEnabled = false

        cards.forEach { (card, level) ->
            card.setOnClickListener { select(level, btnNext) }
        }

        btnNext.setOnClickListener {
            prefs.edit().putString("activity_level", selectedLevel).apply()
            goToNext()
        }
    }

    private fun select(level: String, btnNext: MaterialButton) {
        selectedLevel = level
        val primary = themeColor(android.R.attr.colorPrimary)

        cards.forEach { (card, cardLevel) ->
            if (cardLevel == level) {
                card.strokeColor = primary
                card.strokeWidth = dp(2)
                card.setCardBackgroundColor(ColorUtils.setAlphaComponent(primary, 40))
            } else {
                card.strokeColor = androidx.core.content.ContextCompat.getColor(this, R.color.card_stroke_unselected)
                card.strokeWidth = dp(1)
                card.setCardBackgroundColor(Color.TRANSPARENT)
            }
        }

        btnNext.isEnabled = true
    }

    private fun goToNext() {
        startActivity(Intent(this, MacroResultActivity::class.java))
        finish()
    }

    private fun themeColor(@AttrRes attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        /** Returns the TDEE multiplier for a stored activity level key. */
        fun multiplier(level: String) = when (level) {
            "sedentary" -> 1.2
            "light"     -> 1.375
            "moderate"  -> 1.55
            "active"    -> 1.725
            "extra"     -> 1.9
            else        -> 1.55
        }
    }
}
