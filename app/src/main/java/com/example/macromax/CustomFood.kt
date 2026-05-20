package com.example.macromax

import java.util.UUID

data class CustomFood(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kcalPer100g: Int,
    val proteinPer100g: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float
)
