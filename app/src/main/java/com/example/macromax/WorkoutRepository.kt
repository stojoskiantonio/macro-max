package com.example.macromax

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object WorkoutRepository {

    private const val PREFIX = "workout_log_"

    fun load(prefs: SharedPreferences, dateKey: String): List<WorkoutEntry> {
        val arr = JSONArray(prefs.getString("$PREFIX$dateKey", "[]") ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            WorkoutEntry(
                id              = o.optString("id", UUID.randomUUID().toString()),
                exerciseType    = o.optString("type", "other"),
                exerciseName    = o.optString("name", "Other"),
                durationMinutes = o.optInt("duration", 0),
                caloriesBurned  = o.optInt("calories", 0)
            )
        }
    }

    fun save(prefs: SharedPreferences, dateKey: String, entry: WorkoutEntry) {
        val arr = JSONArray(prefs.getString("$PREFIX$dateKey", "[]") ?: "[]")
        arr.put(JSONObject().apply {
            put("id",       entry.id)
            put("type",     entry.exerciseType)
            put("name",     entry.exerciseName)
            put("duration", entry.durationMinutes)
            put("calories", entry.caloriesBurned)
        })
        prefs.edit().putString("$PREFIX$dateKey", arr.toString()).apply()
    }

    fun delete(prefs: SharedPreferences, dateKey: String, id: String) {
        val arr  = JSONArray(prefs.getString("$PREFIX$dateKey", "[]") ?: "[]")
        val keep = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") != id) keep.put(o)
        }
        prefs.edit().putString("$PREFIX$dateKey", keep.toString()).apply()
    }
}
