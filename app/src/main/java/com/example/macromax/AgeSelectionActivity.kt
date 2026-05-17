package com.example.macromax

import android.content.Intent
import android.os.Bundle
import android.widget.NumberPicker
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class AgeSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        if (prefs.getInt("user_age", -1) != -1) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_age_selection)

        val agePicker = findViewById<NumberPicker>(R.id.agePicker).apply {
            minValue = 10
            maxValue = 100
            value = 25
            wrapSelectorWheel = false
        }

        findViewById<MaterialButton>(R.id.btnNext).setOnClickListener {
            prefs.edit().putInt("user_age", agePicker.value).apply()
            goToMain()
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, WeightSelectionActivity::class.java))
        finish()
    }
}
