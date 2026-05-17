package com.example.macromax

data class DayHistory(
    val dateKey: String,          // "20260517"
    val displayDate: String,      // "Saturday, May 17, 2026"
    val entries: List<FoodEntry>,
    val totalCalories: Int,
    val totalProtein: Int,
    val totalFat: Int,
    val totalCarbs: Int
)
