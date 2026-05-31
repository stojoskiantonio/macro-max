package com.example.macromax

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Centralised Firebase Analytics helper.
 * All event names and parameter keys live here so they're easy to audit.
 */
object Analytics {

    private val fa: FirebaseAnalytics get() = Firebase.analytics

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun logLogin(method: String) {
        fa.logEvent(FirebaseAnalytics.Event.LOGIN, Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
        })
    }

    fun logSignUp(method: String) {
        fa.logEvent(FirebaseAnalytics.Event.SIGN_UP, Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
        })
    }

    // ── Food ──────────────────────────────────────────────────────────────────

    fun logFoodLogged(name: String, calories: Int, mealType: String, source: String) {
        // source: "search" | "barcode" | "manual" | "favourite" | "recipe" | "recent"
        fa.logEvent("food_logged", Bundle().apply {
            putString("food_name",  name.take(100))
            putInt   ("calories",   calories)
            putString("meal_type",  mealType)
            putString("source",     source)
        })
    }

    fun logFoodDeleted(mealType: String) {
        fa.logEvent("food_deleted", Bundle().apply {
            putString("meal_type", mealType)
        })
    }

    fun logBarcodeScanned(success: Boolean) {
        fa.logEvent("barcode_scanned", Bundle().apply {
            putString("result", if (success) "found" else "not_found")
        })
    }

    // ── Workouts ──────────────────────────────────────────────────────────────

    fun logWorkoutLogged(exerciseType: String, durationMin: Int, caloriesBurned: Int) {
        fa.logEvent("workout_logged", Bundle().apply {
            putString("exercise_type",   exerciseType)
            putInt   ("duration_min",    durationMin)
            putInt   ("calories_burned", caloriesBurned)
        })
    }

    fun logWorkoutDeleted(exerciseType: String) {
        fa.logEvent("workout_deleted", Bundle().apply {
            putString("exercise_type", exerciseType)
        })
    }

    // ── Water ─────────────────────────────────────────────────────────────────

    fun logWaterUpdated(glasses: Int) {
        fa.logEvent("water_updated", Bundle().apply {
            putInt("glasses", glasses)
        })
    }

    // ── Profile & settings ────────────────────────────────────────────────────

    fun logProfileUpdated(fieldsChanged: String) {
        fa.logEvent("profile_updated", Bundle().apply {
            putString("fields", fieldsChanged)
        })
    }

    fun logGoalUpdated(calorieTarget: Int) {
        fa.logEvent("goal_updated", Bundle().apply {
            putInt("calorie_target", calorieTarget)
        })
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    fun logScreenView(context: Context, screenName: String) {
        fa.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME,  screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, context.javaClass.simpleName)
        })
    }
}
