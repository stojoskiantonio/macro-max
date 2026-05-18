package com.example.macromax

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREF_GEMINI_KEY = "gemini_api_key"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.btnSettingsBack).setOnClickListener { finish() }

        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)

        // ── Light mode ──────────────────────────────────────────────────────────
        val switchLightMode = findViewById<SwitchMaterial>(R.id.switchLightMode)
        switchLightMode.isChecked = prefs.getBoolean("light_mode", false)
        switchLightMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("light_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_NO
                else           AppCompatDelegate.MODE_NIGHT_YES
            )
        }

        // ── Language ────────────────────────────────────────────────────────────
        val toggleLanguage = findViewById<MaterialButtonToggleGroup>(R.id.toggleLanguage)
        val currentLang = AppCompatDelegate.getApplicationLocales()
            .let { if (it.isEmpty) "en" else (it[0]?.language ?: "en") }
        toggleLanguage.check(if (currentLang == "mk") R.id.btnLangMacedonian else R.id.btnLangEnglish)
        toggleLanguage.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val tag = if (checkedId == R.id.btnLangMacedonian) "mk" else "en"
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }

    }
}
