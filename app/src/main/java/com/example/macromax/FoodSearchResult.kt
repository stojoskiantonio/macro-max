package com.example.macromax

data class FoodSearchResult(
    val name: String,
    val brand: String,
    val caloriesPer100g: Int,
    val proteinPer100g: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float
)
