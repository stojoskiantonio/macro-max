package com.example.macromax

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class RecipeEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var searchJob: Job? = null

    private lateinit var tvTitle:            TextView
    private lateinit var etRecipeName:       TextInputEditText
    private lateinit var tilRecipeName:      TextInputLayout
    private lateinit var etRecipeServings:   TextInputEditText
    private lateinit var containerIngredients: LinearLayout
    private lateinit var tvTotalsPerServing: TextView
    private lateinit var tvTotalsTotal:      TextView
    private lateinit var paneEdit:           NestedScrollView
    private lateinit var paneSearch:         LinearLayout
    private lateinit var etIngSearch:        TextInputEditText
    private lateinit var progressIngSearch:  CircularProgressIndicator
    private lateinit var rvIngResults:       RecyclerView
    private lateinit var tvIngSearchHint:    TextView
    private lateinit var tvIngNoResults:     TextView

    private val ingredients = mutableListOf<RecipeIngredient>()
    private var editingRecipeId: String? = null

    private val prefs get() = getSharedPreferences("macromax_prefs", MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_edit)

        tvTitle            = findViewById(R.id.tvRecipeEditTitle)
        etRecipeName       = findViewById(R.id.etRecipeName)
        tilRecipeName      = findViewById(R.id.tilRecipeName)
        etRecipeServings   = findViewById(R.id.etRecipeServings)
        containerIngredients = findViewById(R.id.containerIngredients)
        tvTotalsPerServing = findViewById(R.id.tvTotalsPerServing)
        tvTotalsTotal      = findViewById(R.id.tvTotalsTotal)
        paneEdit           = findViewById(R.id.paneEdit)
        paneSearch         = findViewById(R.id.paneSearch)
        etIngSearch        = findViewById(R.id.etIngSearch)
        progressIngSearch  = findViewById(R.id.progressIngSearch)
        rvIngResults       = findViewById(R.id.rvIngResults)
        tvIngSearchHint    = findViewById(R.id.tvIngSearchHint)
        tvIngNoResults     = findViewById(R.id.tvIngNoResults)

        rvIngResults.layoutManager = LinearLayoutManager(this)

        // Back button: if in search pane, go back to edit pane; otherwise finish
        findViewById<ImageButton>(R.id.btnRecipeEditBack).setOnClickListener {
            if (paneSearch.visibility == View.VISIBLE) showEditPane()
            else finish()
        }

        // Save
        findViewById<MaterialButton>(R.id.btnSaveRecipe).setOnClickListener { saveRecipe() }

        // Add ingredient → open search pane
        findViewById<MaterialButton>(R.id.btnAddIngredient).setOnClickListener {
            showSearchPane()
        }

        // Manual ingredient entry
        findViewById<MaterialButton>(R.id.btnIngManual).setOnClickListener {
            showManualIngredientDialog()
        }

        // Ingredient search
        etIngSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { onIngQueryChanged(s?.toString()?.trim() ?: "") }
        })

        // Servings change → refresh totals
        etRecipeServings.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateTotals() }
        })

        // Load existing recipe if editing
        val recipeId = intent.getStringExtra(EXTRA_RECIPE_ID)
        if (recipeId != null) {
            editingRecipeId = recipeId
            tvTitle.text = getString(R.string.recipe_edit_title)
            RecipeRepository.load(prefs).find { it.id == recipeId }?.let { recipe ->
                etRecipeName.setText(recipe.name)
                etRecipeServings.setText(recipe.servings.toString())
                ingredients.addAll(recipe.ingredients)
                refreshIngredientRows()
            }
        }
        updateTotals()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBackPressed() {
        if (paneSearch.visibility == View.VISIBLE) showEditPane()
        else super.onBackPressed()
    }

    // ── Pane switching ────────────────────────────────────────────────────────

    private fun showEditPane() {
        paneEdit.visibility   = View.VISIBLE
        paneSearch.visibility = View.GONE
        etIngSearch.setText("")
        showIngState(IngState.HINT)
        searchJob?.cancel()
    }

    private fun showSearchPane() {
        paneEdit.visibility   = View.GONE
        paneSearch.visibility = View.VISIBLE
        etIngSearch.requestFocus()
    }

    // ── Ingredient search ─────────────────────────────────────────────────────

    private enum class IngState { HINT, LOADING, RESULTS, NO_RESULTS }

    private fun showIngState(state: IngState) {
        tvIngSearchHint.visibility   = if (state == IngState.HINT)       View.VISIBLE else View.GONE
        progressIngSearch.visibility = if (state == IngState.LOADING)    View.VISIBLE else View.GONE
        rvIngResults.visibility      = if (state == IngState.RESULTS)    View.VISIBLE else View.GONE
        tvIngNoResults.visibility    = if (state == IngState.NO_RESULTS) View.VISIBLE else View.GONE
    }

    private fun onIngQueryChanged(query: String) {
        searchJob?.cancel()
        if (query.length < 2) { showIngState(IngState.HINT); return }
        showIngState(IngState.LOADING)
        searchJob = scope.launch {
            delay(500)
            val results = try { searchFoods(query) } catch (e: Exception) { null }
            when {
                results == null   -> { tvIngNoResults.text = getString(R.string.search_error);      showIngState(IngState.NO_RESULTS) }
                results.isEmpty() -> { tvIngNoResults.text = getString(R.string.search_no_results); showIngState(IngState.NO_RESULTS) }
                else              -> {
                    rvIngResults.adapter = FoodSearchAdapter(results) { food -> showPortionDialog(food) }
                    showIngState(IngState.RESULTS)
                }
            }
        }
    }

    private suspend fun searchFoods(query: String): List<FoodSearchResult> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL(
                "https://world.openfoodfacts.org/cgi/search.pl" +
                "?search_terms=$encoded&search_simple=1&action=process&json=1&page_size=25" +
                "&fields=product_name,brands,nutriments"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000; conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "MacroMax Android App")
            try {
                val body = conn.inputStream.bufferedReader().readText()
                val products = org.json.JSONObject(body).getJSONArray("products")
                val results  = mutableListOf<FoodSearchResult>()
                for (i in 0 until products.length()) {
                    val p    = products.getJSONObject(i)
                    val name = p.optString("product_name").trim().ifEmpty { continue }
                    val n    = p.optJSONObject("nutriments") ?: continue
                    val kcal = n.optDouble("energy-kcal_100g", -1.0)
                    if (kcal < 0) continue
                    results.add(FoodSearchResult(
                        name            = name,
                        brand           = p.optString("brands").split(",").first().trim(),
                        caloriesPer100g = kcal.toInt(),
                        proteinPer100g  = n.optDouble("proteins_100g",     0.0).toFloat(),
                        fatPer100g      = n.optDouble("fat_100g",          0.0).toFloat(),
                        carbsPer100g    = n.optDouble("carbohydrates_100g",0.0).toFloat()
                    ))
                    if (results.size >= 20) break
                }
                results
            } finally { conn.disconnect() }
        }

    // ── Portion dialog (when adding from search) ──────────────────────────────

    private fun showPortionDialog(food: FoodSearchResult) {
        val dp   = resources.displayMetrics.density
        val hPad = (24 * dp).toInt()

        val tilGrams = TextInputLayout(this, null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            hint = getString(R.string.hint_amount_g); suffixText = "g"
            setPadding(hPad, 0, hPad, 0)
        }
        val etGrams = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        tilGrams.addView(etGrams)

        MaterialAlertDialogBuilder(this)
            .setTitle(food.name)
            .setMessage("${food.caloriesPer100g} kcal  ·  P ${food.proteinPer100g.toInt()}g  F ${food.fatPer100g.toInt()}g  C ${food.carbsPer100g.toInt()}g\n${getString(R.string.per_100g)}")
            .setView(tilGrams)
            .setPositiveButton(R.string.btn_add) { _, _ ->
                val grams = etGrams.text.toString().toFloatOrNull()
                if (grams == null || grams <= 0f) {
                    Toast.makeText(this, getString(R.string.error_enter_amount), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val f = grams / 100f
                addIngredient(RecipeIngredient(
                    name     = food.name,
                    grams    = grams,
                    calories = (food.caloriesPer100g * f).toInt(),
                    proteinG = (food.proteinPer100g  * f).toInt(),
                    fatG     = (food.fatPer100g      * f).toInt(),
                    carbsG   = (food.carbsPer100g    * f).toInt()
                ))
                showEditPane()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Manual ingredient dialog ──────────────────────────────────────────────

    private fun showManualIngredientDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_manual_food, null)
        // Hide the "save as favourite" checkbox — not needed here
        view.findViewById<View>(R.id.cbSaveFavourite).visibility = View.GONE

        val tilName = view.findViewById<TextInputLayout>(R.id.tilManualName)
        val tilCal  = view.findViewById<TextInputLayout>(R.id.tilManualCalories)
        val etName  = view.findViewById<TextInputEditText>(R.id.etManualName)
        val etCal   = view.findViewById<TextInputEditText>(R.id.etManualCalories)
        val etPro   = view.findViewById<TextInputEditText>(R.id.etManualProtein)
        val etFat   = view.findViewById<TextInputEditText>(R.id.etManualFat)
        val etCarbs = view.findViewById<TextInputEditText>(R.id.etManualCarbs)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.btn_add_manually)
            .setView(view)
            .setPositiveButton(R.string.btn_add) { _, _ ->
                val name   = etName.text.toString().trim()
                val calStr = etCal.text.toString().trim()
                var valid  = true
                if (name.isEmpty())   { tilName.error = getString(R.string.error_required); valid = false } else tilName.error = null
                if (calStr.isEmpty()) { tilCal.error  = getString(R.string.error_required); valid = false } else tilCal.error  = null
                if (!valid) return@setPositiveButton
                addIngredient(RecipeIngredient(
                    name     = name,
                    grams    = 0f,
                    calories = calStr.toIntOrNull()                         ?: 0,
                    proteinG = etPro.text.toString().trim().toIntOrNull()   ?: 0,
                    fatG     = etFat.text.toString().trim().toIntOrNull()   ?: 0,
                    carbsG   = etCarbs.text.toString().trim().toIntOrNull() ?: 0
                ))
                showEditPane()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Ingredient list management ────────────────────────────────────────────

    private fun addIngredient(ing: RecipeIngredient) {
        ingredients.add(ing)
        refreshIngredientRows()
        updateTotals()
    }

    private fun refreshIngredientRows() {
        containerIngredients.removeAllViews()
        ingredients.forEachIndexed { idx, ing ->
            val row = buildIngredientRow(ing, idx)
            containerIngredients.addView(row)
            if (idx < ingredients.size - 1) {
                val div = View(this)
                div.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (0.75f * resources.displayMetrics.density).toInt()
                )
                div.setBackgroundColor(getColor(R.color.divider_line))
                containerIngredients.addView(div)
            }
        }
    }

    private fun buildIngredientRow(ing: RecipeIngredient, idx: Int): View {
        val dp  = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }

        val tvInfo = TextView(this).apply {
            text = "${ing.name}${if (ing.grams > 0f) " (${ing.grams.toInt()}g)" else ""}\n${ing.calories} kcal  P ${ing.proteinG}g  F ${ing.fatG}g  C ${ing.carbsG}g"
            textSize = 13f
            setTextColor(currentTextColor)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnDel = ImageButton(this).apply {
            setImageResource(R.drawable.ic_delete)
            background = getDrawable(android.R.color.transparent)
            alpha = 0.5f
            val size = dp(36)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener {
                ingredients.removeAt(idx)
                refreshIngredientRows()
                updateTotals()
            }
        }

        row.addView(tvInfo)
        row.addView(btnDel)
        return row
    }

    // ── Totals ────────────────────────────────────────────────────────────────

    private fun updateTotals() {
        val servings = etRecipeServings.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
        val totalCal  = ingredients.sumOf { it.calories }
        val totalPro  = ingredients.sumOf { it.proteinG }
        val totalFat  = ingredients.sumOf { it.fatG }
        val totalCarb = ingredients.sumOf { it.carbsG }

        val perCal  = totalCal  / servings
        val perPro  = totalPro  / servings
        val perFat  = totalFat  / servings
        val perCarb = totalCarb / servings

        tvTotalsPerServing.text = getString(R.string.recipe_per_serving,
            servings, perCal, perPro, perFat, perCarb)
        tvTotalsTotal.text = getString(R.string.recipe_total_all,
            totalCal, totalPro, totalFat, totalCarb)
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private fun saveRecipe() {
        val name     = etRecipeName.text.toString().trim()
        val servings = etRecipeServings.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1

        if (name.isEmpty()) {
            tilRecipeName.error = getString(R.string.error_required)
            return
        }
        tilRecipeName.error = null

        if (ingredients.isEmpty()) {
            Snackbar.make(
                findViewById(R.id.btnSaveRecipe),
                getString(R.string.recipe_no_ingredients),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val recipe = Recipe(
            id          = editingRecipeId ?: UUID.randomUUID().toString(),
            name        = name,
            servings    = servings,
            ingredients = ingredients.toList()
        )
        RecipeRepository.save(prefs, recipe)
        Toast.makeText(this, getString(R.string.recipe_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
