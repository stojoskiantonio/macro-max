package com.example.macromax

data class WorkoutEntry(
    val id: String,
    val exerciseType: String,
    val exerciseName: String,
    val durationMinutes: Int,
    val caloriesBurned: Int
)
