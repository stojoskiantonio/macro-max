package com.example.macromax

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object FavouritesRepository {

    private const val KEY = "favourite_foods"

    fun load(prefs: SharedPreferences): List<FavouriteFood> {
        val arr = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        val list = mutableListOf<FavouriteFood>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                FavouriteFood(
                    name     = o.optString("name"),
                    calories = o.optInt("cal"),
                    proteinG = o.optInt("pro"),
                    fatG     = o.optInt("fat"),
                    carbsG   = o.optInt("car")
                )
            )
        }
        return list
    }

    fun save(prefs: SharedPreferences, item: FavouriteFood) {
        val list = load(prefs).toMutableList()
        // Replace if name already exists, otherwise prepend
        val idx = list.indexOfFirst { it.name.equals(item.name, ignoreCase = true) }
        if (idx >= 0) list[idx] = item else list.add(0, item)
        persist(prefs, list)
    }

    fun delete(prefs: SharedPreferences, name: String) {
        val list = load(prefs).filter { !it.name.equals(name, ignoreCase = true) }
        persist(prefs, list)
    }

    private fun persist(prefs: SharedPreferences, list: List<FavouriteFood>) {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(JSONObject().apply {
                put("name", f.name)
                put("cal",  f.calories)
                put("pro",  f.proteinG)
                put("fat",  f.fatG)
                put("car",  f.carbsG)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}
