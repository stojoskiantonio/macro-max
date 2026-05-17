package com.example.macromax

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlin.math.roundToInt

class HeightSelectionActivity : AppCompatActivity() {

    private var isCm = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        if (prefs.getInt("height_cm", -1) != -1) {
            goToNext()
            return
        }

        setContentView(R.layout.activity_height_selection)

        val cmPicker = findViewById<NumberPicker>(R.id.cmPicker).apply {
            minValue = 100; maxValue = 250; value = 170; wrapSelectorWheel = false
        }
        val ftPicker = findViewById<NumberPicker>(R.id.ftPicker).apply {
            minValue = 4; maxValue = 7; value = 5; wrapSelectorWheel = false
        }
        val inPicker = findViewById<NumberPicker>(R.id.inPicker).apply {
            minValue = 0; maxValue = 11; value = 7; wrapSelectorWheel = false
        }
        val cmView = findViewById<NumberPicker>(R.id.cmPicker)
        val ftInView = findViewById<LinearLayout>(R.id.ftInContainer)
        val unitToggle = findViewById<MaterialButtonToggleGroup>(R.id.unitToggle)

        unitToggle.check(R.id.btnCm)

        unitToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            isCm = checkedId == R.id.btnCm
            if (isCm) {
                cmView.visibility = View.VISIBLE
                ftInView.visibility = View.GONE
            } else {
                cmView.visibility = View.GONE
                ftInView.visibility = View.VISIBLE
            }
        }

        findViewById<MaterialButton>(R.id.btnNext).setOnClickListener {
            val heightCm = if (isCm) {
                cmPicker.value
            } else {
                (ftPicker.value * 30.48 + inPicker.value * 2.54).roundToInt()
            }
            prefs.edit().putInt("height_cm", heightCm).apply()
            goToNext()
        }
    }

    private fun goToNext() {
        startActivity(Intent(this, GenderSelectionActivity::class.java))
        finish()
    }
}
