package com.example.macromax

import android.content.Intent
import android.os.Bundle
import android.widget.NumberPicker
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlin.math.roundToInt

class WeightSelectionActivity : AppCompatActivity() {

    private var isLbs = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        if (prefs.getInt("weight_value", -1) != -1) {
            goToNext()
            return
        }

        setContentView(R.layout.activity_weight_selection)

        val weightPicker = findViewById<NumberPicker>(R.id.weightPicker).apply {
            wrapSelectorWheel = false
        }
        val unitToggle = findViewById<MaterialButtonToggleGroup>(R.id.unitToggle)

        setUnit(weightPicker, lbs = true)
        unitToggle.check(R.id.btnLbs)

        unitToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val switchToLbs = checkedId == R.id.btnLbs
            if (switchToLbs == isLbs) return@addOnButtonCheckedListener

            val converted = if (switchToLbs) {
                (weightPicker.value * 2.205).roundToInt().coerceIn(50, 500)
            } else {
                (weightPicker.value / 2.205).roundToInt().coerceIn(20, 300)
            }
            setUnit(weightPicker, lbs = switchToLbs)
            weightPicker.value = converted
        }

        findViewById<MaterialButton>(R.id.btnNext).setOnClickListener {
            prefs.edit()
                .putInt("weight_value", weightPicker.value)
                .putString("weight_unit", if (isLbs) "lbs" else "kg")
                .apply()
            goToNext()
        }
    }

    private fun setUnit(picker: NumberPicker, lbs: Boolean) {
        isLbs = lbs
        if (lbs) {
            picker.minValue = 50
            picker.maxValue = 500
            picker.value = picker.value.coerceIn(50, 500).takeIf { it != 0 } ?: 154
        } else {
            picker.minValue = 20
            picker.maxValue = 300
            picker.value = picker.value.coerceIn(20, 300).takeIf { it != 0 } ?: 70
        }
    }

    private fun goToNext() {
        startActivity(Intent(this, HeightSelectionActivity::class.java))
        finish()
    }
}
