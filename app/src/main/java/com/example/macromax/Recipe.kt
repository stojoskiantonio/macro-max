package com.example.macromax

data class RecipeIngredient(
    val name: String,
    val grams: Float,
    val calories: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int
)

data class Recipe(
    val id: String,
    val name: String,
    val servings: Int,
    val ingredients: List<RecipeIngredient>
) {
    val totalCalories: Int get() = ingredients.sumOf { it.calories }
    val totalProtein:  Int get() = ingredients.sumOf { it.proteinG }
    val totalFat:      Int get() = ingredients.sumOf { it.fatG }
    val totalCarbs:    Int get() = ingredients.sumOf { it.carbsG }

    private val s get() = servings.coerceAtLeast(1)
    val calPerServing:  Int get() = totalCalories / s
    val protPerServing: Int get() = totalProtein  / s
    val fatPerServing:  Int get() = totalFat      / s
    val carbPerServing: Int get() = totalCarbs    / s
}
