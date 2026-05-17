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

class GoalSelectionActivity : AppCompatActivity() {

    private var selectedGoal: String? = null
    private val cards = mutableListOf<Pair<MaterialCardView, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        if (prefs.getString("user_goal", null) != null) {
            goToNext()
            return
        }

        setContentView(R.layout.activity_goal_selection)

        val cardLose = findViewById<MaterialCardView>(R.id.cardLose)
        val cardMaintain = findViewById<MaterialCardView>(R.id.cardMaintain)
        val cardGain = findViewById<MaterialCardView>(R.id.cardGain)
        val btnNext = findViewById<MaterialButton>(R.id.btnNext)

        cards.addAll(listOf(cardLose to "lose", cardMaintain to "maintain", cardGain to "gain"))
        btnNext.isEnabled = false

        cards.forEach { (card, goal) ->
            card.setOnClickListener { select(goal, btnNext) }
        }

        btnNext.setOnClickListener {
            prefs.edit().putString("user_goal", selectedGoal).apply()
            goToNext()
        }
    }

    private fun select(goal: String, btnNext: MaterialButton) {
        selectedGoal = goal
        val primary = themeColor(android.R.attr.colorPrimary)

        cards.forEach { (card, cardGoal) ->
            if (cardGoal == goal) {
                card.strokeColor = primary
                card.strokeWidth = dp(2)
                card.setCardBackgroundColor(ColorUtils.setAlphaComponent(primary, 40))
            } else {
                card.strokeColor = Color.parseColor("#44FFFFFF")
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
}
