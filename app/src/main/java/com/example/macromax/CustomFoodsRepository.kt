package com.example.macromax

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object CustomFoodsRepository {

    private const val KEY = "custom_foods"

    fun load(prefs: SharedPreferences): List<CustomFood> {
        val arr = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CustomFood(
                id             = o.optString("id"),
                name           = o.optString("name"),
                kcalPer100g    = o.optInt("kcal"),
                proteinPer100g = o.optDouble("pro", 0.0).toFloat(),
                fatPer100g     = o.optDouble("fat", 0.0).toFloat(),
                carbsPer100g   = o.optDouble("car", 0.0).toFloat()
            )
        }
    }

    fun save(prefs: SharedPreferences, food: CustomFood) {
        val list = load(prefs).toMutableList()
        val idx = list.indexOfFirst { it.id == food.id }
        if (idx >= 0) list[idx] = food else list.add(food)
        persist(prefs, list)
    }

    fun delete(prefs: SharedPreferences, id: String) {
        persist(prefs, load(prefs).filter { it.id != id })
    }

    private fun persist(prefs: SharedPreferences, list: List<CustomFood>) {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(JSONObject().apply {
                put("id",   f.id)
                put("name", f.name)
                put("kcal", f.kcalPer100g)
                put("pro",  f.proteinPer100g.toDouble())
                put("fat",  f.fatPer100g.toDouble())
                put("car",  f.carbsPer100g.toDouble())
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}
