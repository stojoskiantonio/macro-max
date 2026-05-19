package com.example.macromax

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object RecipeRepository {

    private const val KEY = "recipes"

    fun load(prefs: SharedPreferences): List<Recipe> {
        val arr  = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        val list = mutableListOf<Recipe>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ingArr = o.optJSONArray("ingredients") ?: JSONArray()
            val ingredients = (0 until ingArr.length()).map { j ->
                val ing = ingArr.getJSONObject(j)
                RecipeIngredient(
                    name     = ing.optString("name"),
                    grams    = ing.optDouble("grams", 0.0).toFloat(),
                    calories = ing.optInt("cal"),
                    proteinG = ing.optInt("pro"),
                    fatG     = ing.optInt("fat"),
                    carbsG   = ing.optInt("car")
                )
            }
            list.add(
                Recipe(
                    id          = o.optString("id", UUID.randomUUID().toString()),
                    name        = o.optString("name"),
                    servings    = o.optInt("servings", 1).coerceAtLeast(1),
                    ingredients = ingredients
                )
            )
        }
        return list
    }

    fun save(prefs: SharedPreferences, recipe: Recipe) {
        val list = load(prefs).toMutableList()
        val idx  = list.indexOfFirst { it.id == recipe.id }
        if (idx >= 0) list[idx] = recipe else list.add(recipe)
        persist(prefs, list)
    }

    fun delete(prefs: SharedPreferences, id: String) {
        persist(prefs, load(prefs).filter { it.id != id })
    }

    private fun persist(prefs: SharedPreferences, list: List<Recipe>) {
        val arr = JSONArray()
        list.forEach { r ->
            val ingArr = JSONArray()
            r.ingredients.forEach { ing ->
                ingArr.put(JSONObject().apply {
                    put("name",  ing.name)
                    put("grams", ing.grams)
                    put("cal",   ing.calories)
                    put("pro",   ing.proteinG)
                    put("fat",   ing.fatG)
                    put("car",   ing.carbsG)
                })
            }
            arr.put(JSONObject().apply {
                put("id",          r.id)
                put("name",        r.name)
                put("servings",    r.servings)
                put("ingredients", ingArr)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}
