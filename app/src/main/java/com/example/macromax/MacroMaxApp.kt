package com.example.macromax

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class MacroMaxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
}
