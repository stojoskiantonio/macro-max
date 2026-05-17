package com.example.macromax

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.btnSettingsBack).setOnClickListener { finish() }

        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val switchLightMode = findViewById<SwitchMaterial>(R.id.switchLightMode)

        // Reflect current saved state
        switchLightMode.isChecked = prefs.getBoolean("light_mode", false)

        switchLightMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("light_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_NO
                else           AppCompatDelegate.MODE_NIGHT_YES
            )
        }
    }
}
