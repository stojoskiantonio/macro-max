package com.example.macromax

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogFoodActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var searchJob: Job? = null

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val barcode = result.data?.getStringExtra(BarcodeScanActivity.EXTRA_BARCODE) ?: return@registerForActivityResult
            lookupBarcode(barcode)
        }
    }

    private lateinit var cgMealType: ChipGroup
    private lateinit var toggleTab: TabLayout
    private lateinit var paneSearch: LinearLayout
    private lateinit var paneFavourites: View
    private lateinit var paneRecipes: View
    private lateinit var rvResults: RecyclerView
    private lateinit var rvFavourites: RecyclerView
    private lateinit var rvRecipesLog: RecyclerView
    private lateinit var progressSearch: CircularProgressIndicator
    private lateinit var tvSearchHint: TextView
    private lateinit var layoutNoResults: View
    private lateinit var tvNoResults: TextView
    private lateinit var tvFavEmpty: TextView
    private lateinit var tvRecipesEmpty: TextView
    private lateinit var scrollRecentFoods: View
    private lateinit var cgRecentFoods: ChipGroup
    private lateinit var paneMyFoods: View
    private lateinit var rvMyFoods: RecyclerView
    private lateinit var tvMyFoodsEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_food)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        cgMealType      = findViewById(R.id.cgMealType)
        toggleTab       = findViewById(R.id.toggleTab)
        paneSearch      = findViewById(R.id.paneSearch)
        paneFavourites  = findViewById(R.id.paneFavourites)
        paneRecipes     = findViewById(R.id.paneRecipes)
        rvResults       = findViewById(R.id.rvSearchResults)
        rvFavourites    = findViewById(R.id.rvFavourites)
        rvRecipesLog    = findViewById(R.id.rvRecipesLog)
        progressSearch  = findViewById(R.id.progressSearch)
        tvSearchHint    = findViewById(R.id.tvSearchHint)
        layoutNoResults = findViewById(R.id.layoutNoResults)
        tvNoResults     = findViewById(R.id.tvNoResults)
        tvFavEmpty      = findViewById(R.id.tvFavEmpty)
        tvRecipesEmpty  = findViewById(R.id.tvRecipesEmpty)
        scrollRecentFoods = findViewById(R.id.scrollRecentFoods)
        cgRecentFoods   = findViewById(R.id.cgRecentFoods)
        paneMyFoods     = findViewById(R.id.paneMyFoods)
        rvMyFoods       = findViewById(R.id.rvMyFoods)
        tvMyFoodsEmpty  = findViewById(R.id.tvMyFoodsEmpty)

        // Pre-select meal type based on time of day
        val defaultChipId = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..9   -> R.id.chipBreakfast
            in 10..14 -> R.id.chipLunch
            in 15..20 -> R.id.chipDinner
            else      -> R.id.chipSnack
        }
        findViewById<com.google.android.material.chip.Chip>(defaultChipId).isChecked = true

        rvResults.layoutManager    = LinearLayoutManager(this)
        rvFavourites.layoutManager = LinearLayoutManager(this)
        rvRecipesLog.layoutManager = LinearLayoutManager(this)
        rvMyFoods.layoutManager    = LinearLayoutManager(this)

        // Tab bar — index order: 0=Search, 1=Favourites, 2=Recipes, 3=My Foods
        toggleTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> showSearchPane()
                    1 -> showFavouritesPane()
                    2 -> showRecipesPane()
                    3 -> showMyFoodsPane()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        toggleTab.selectTab(toggleTab.getTabAt(0))

        // Live search with 500 ms debounce
        findViewById<TextInputEditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                onQueryChanged(query)
            }
        })

        // Barcode scan button
        findViewById<ImageButton>(R.id.btnScanBarcode).setOnClickListener {
            scanLauncher.launch(Intent(this, BarcodeScanActivity::class.java))
        }

        // Manual entry fallback
        findViewById<MaterialButton>(R.id.btnManualEntry).setOnClickListener {
            showManualEntryDialog()
        }

        // Manage recipes button
        findViewById<MaterialButton>(R.id.btnManageRecipes).setOnClickListener {
            startActivity(Intent(this, RecipeListActivity::class.java))
        }

        // New custom food button
        findViewById<MaterialButton>(R.id.btnNewCustomFood).setOnClickListener {
            showCustomFoodDialog(null)
        }

        // Recent foods chips
        loadRecentFoods()
    }

    // ── Recent foods chips ────────────────────────────────────────────────────

    private fun loadRecentFoods() {
        val prefs  = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val fmt    = SimpleDateFormat("yyyyMMdd", Locale.US)
        val seen   = linkedSetOf<String>()   // insertion-ordered unique names
        val foods  = mutableListOf<FoodEntry>()

        // Scan the last 14 days of logs, newest entries first
        val cal = Calendar.getInstance()
        outer@ for (d in 0..13) {
            if (d > 0) cal.add(Calendar.DAY_OF_YEAR, -1)
            val key  = fmt.format(cal.time)
            val arr  = JSONArray(prefs.getString("food_log_$key", "[]") ?: "[]")
            // Iterate in reverse so most recent entries come first
            for (i in arr.length() - 1 downTo 0) {
                val obj  = arr.getJSONObject(i)
                val name = obj.optString("name").trim()
                if (name.isNotEmpty() && seen.add(name)) {
                    foods.add(FoodEntry(
                        name     = name,
                        calories = obj.optInt("cal"),
                        proteinG = obj.optInt("pro"),
                        fatG     = obj.optInt("fat"),
                        carbsG   = obj.optInt("car"),
                        mealType = obj.optString("meal", "other")
                    ))
                    if (foods.size >= 5) break@outer
                }
            }
        }

        if (foods.isEmpty()) return

        scrollRecentFoods.visibility = View.VISIBLE
        cgRecentFoods.removeAllViews()
        for (food in foods) {
            val chip = Chip(this).apply {
                text        = "${food.name}  ·  ${food.calories} kcal"
                isCheckable = false
                setOnClickListener { quickAddFood(food) }
            }
            cgRecentFoods.addView(chip)
        }
    }

    private fun quickAddFood(food: FoodEntry) {
        val entry = food.copy(mealType = selectedMealType())
        saveEntry(entry)

        // Undo snackbar — removes the entry we just appended (last item in today's log)
        com.google.android.material.snackbar.Snackbar
            .make(
                findViewById(android.R.id.content),
                getString(R.string.food_added_quick, food.name),
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            )
            .setAction(R.string.undo) {
                undoLastEntry()
            }
            .addCallback(object : com.google.android.material.snackbar.Snackbar.Callback() {
                override fun onDismissed(sb: com.google.android.material.snackbar.Snackbar, event: Int) {
                    if (event != DISMISS_EVENT_ACTION) finish()
                }
            })
            .show()
    }

    private fun undoLastEntry() {
        val prefs  = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val logKey = "food_log_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val arr    = JSONArray(prefs.getString(logKey, "[]") ?: "[]")
        if (arr.length() == 0) return
        val updated = JSONArray()
        for (i in 0 until arr.length() - 1) updated.put(arr.get(i))
        prefs.edit().putString(logKey, updated.toString()).apply()
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Refresh recipes pane if it's currently showing
        if (paneRecipes.visibility == View.VISIBLE) refreshRecipes()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ── Tab switching ─────────────────────────────────────────────────────────

    private fun showSearchPane() {
        paneSearch.visibility     = View.VISIBLE
        paneFavourites.visibility = View.GONE
        paneRecipes.visibility    = View.GONE
        paneMyFoods.visibility    = View.GONE
    }

    private fun showFavouritesPane() {
        paneSearch.visibility     = View.GONE
        paneFavourites.visibility = View.VISIBLE
        paneRecipes.visibility    = View.GONE
        paneMyFoods.visibility    = View.GONE
        refreshFavourites()
    }

    private fun showRecipesPane() {
        paneSearch.visibility     = View.GONE
        paneFavourites.visibility = View.GONE
        paneRecipes.visibility    = View.VISIBLE
        paneMyFoods.visibility    = View.GONE
        refreshRecipes()
    }

    private fun showMyFoodsPane() {
        paneSearch.visibility     = View.GONE
        paneFavourites.visibility = View.GONE
        paneRecipes.visibility    = View.GONE
        paneMyFoods.visibility    = View.VISIBLE
        refreshMyFoods()
    }

    private fun refreshRecipes() {
        val list = RecipeRepository.load(getSharedPreferences("macromax_prefs", MODE_PRIVATE))
        if (list.isEmpty()) {
            rvRecipesLog.visibility  = View.GONE
            tvRecipesEmpty.visibility = View.VISIBLE
        } else {
            tvRecipesEmpty.visibility = View.GONE
            rvRecipesLog.visibility  = View.VISIBLE
            rvRecipesLog.adapter = RecipeAdapter(
                items = list,
                onTap = { recipe -> confirmLogRecipe(recipe) }
            )
        }
    }

    private fun confirmLogRecipe(recipe: Recipe) {
        MaterialAlertDialogBuilder(this)
            .setTitle(recipe.name)
            .setMessage(
                getString(R.string.recipe_log_message,
                    recipe.calPerServing, recipe.protPerServing,
                    recipe.fatPerServing, recipe.carbPerServing)
            )
            .setPositiveButton(R.string.recipe_log_confirm) { _, _ ->
                saveEntry(FoodEntry(
                    name     = recipe.name,
                    calories = recipe.calPerServing,
                    proteinG = recipe.protPerServing,
                    fatG     = recipe.fatPerServing,
                    carbsG   = recipe.carbPerServing,
                    mealType = selectedMealType()
                ))
                Toast.makeText(this, getString(R.string.food_saved), Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshFavourites() {
        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val list  = FavouritesRepository.load(prefs)
        if (list.isEmpty()) {
            rvFavourites.visibility = View.GONE
            tvFavEmpty.visibility   = View.VISIBLE
        } else {
            tvFavEmpty.visibility   = View.GONE
            rvFavourites.visibility = View.VISIBLE
            rvFavourites.adapter = FavouritesAdapter(
                items    = list,
                onAdd    = { fav -> logFavourite(fav) },
                onDelete = { fav -> confirmDeleteFavourite(fav) }
            )
        }
    }

    private fun logFavourite(fav: FavouriteFood) {
        saveEntry(
            FoodEntry(
                name     = fav.name,
                calories = fav.calories,
                proteinG = fav.proteinG,
                fatG     = fav.fatG,
                carbsG   = fav.carbsG,
                mealType = selectedMealType()
            )
        )
        Toast.makeText(this, getString(R.string.food_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDeleteFavourite(fav: FavouriteFood) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_favourite_title)
            .setMessage(getString(R.string.delete_favourite_message, fav.name))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
                FavouritesRepository.delete(prefs, fav.name)
                Toast.makeText(this, getString(R.string.favourite_deleted), Toast.LENGTH_SHORT).show()
                refreshFavourites()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── My Foods ─────────────────────────────────────────────────────────────

    private fun refreshMyFoods() {
        val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val list  = CustomFoodsRepository.load(prefs)
        if (list.isEmpty()) {
            rvMyFoods.visibility      = View.GONE
            tvMyFoodsEmpty.visibility = View.VISIBLE
        } else {
            tvMyFoodsEmpty.visibility = View.GONE
            rvMyFoods.visibility      = View.VISIBLE
            rvMyFoods.adapter = CustomFoodAdapter(
                items    = list,
                onAdd    = { food -> showCustomFoodPortionDialog(food) },
                onEdit   = { food -> showCustomFoodDialog(food) },
                onDelete = { food -> confirmDeleteCustomFood(food) }
            )
        }
    }

    private fun showCustomFoodPortionDialog(food: CustomFood) {
        val dp    = resources.displayMetrics.density
        val hPad  = (24 * dp).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(hPad, 0, hPad, (8 * dp).toInt())
        }

        val tilGrams = TextInputLayout(this, null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            hint       = getString(R.string.hint_amount_g)
            suffixText = "g"
        }
        val etGrams = TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        tilGrams.addView(etGrams)
        container.addView(tilGrams)

        MaterialAlertDialogBuilder(this)
            .setTitle(food.name)
            .setMessage(
                "${food.kcalPer100g} kcal  ·  " +
                "P ${food.proteinPer100g.toInt()}g  " +
                "F ${food.fatPer100g.toInt()}g  " +
                "C ${food.carbsPer100g.toInt()}g" +
                "\n${getString(R.string.per_100g)}"
            )
            .setView(container)
            .setPositiveButton(R.string.btn_add) { _, _ ->
                val grams = etGrams.text.toString().toFloatOrNull()
                if (grams == null || grams <= 0f) {
                    Toast.makeText(this, getString(R.string.error_enter_amount), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val factor = grams / 100f
                saveEntry(FoodEntry(
                    name     = food.name,
                    calories = (food.kcalPer100g    * factor).toInt(),
                    proteinG = (food.proteinPer100g * factor).toInt(),
                    fatG     = (food.fatPer100g     * factor).toInt(),
                    carbsG   = (food.carbsPer100g   * factor).toInt(),
                    mealType = selectedMealType()
                ))
                Toast.makeText(this, getString(R.string.food_saved), Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCustomFoodDialog(existing: CustomFood?) {
        val dp   = resources.displayMetrics.density
        val hPad = (24 * dp).toInt()
        val vGap = (8 * dp).toInt()

        fun makeTil(hint: String, iType: Int): TextInputLayout {
            val et = TextInputEditText(this).apply { inputType = iType }
            return TextInputLayout(this, null,
                com.google.android.material.R.attr.textInputOutlinedStyle
            ).apply {
                this.hint    = hint
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = vGap }
                addView(et)
            }
        }

        val tilName    = makeTil(getString(R.string.custom_food_name_hint),
                             android.text.InputType.TYPE_CLASS_TEXT or
                             android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val tilKcal    = makeTil(getString(R.string.custom_food_kcal_hint),
                             android.text.InputType.TYPE_CLASS_NUMBER)
        val tilProtein = makeTil(getString(R.string.custom_food_protein_hint),
                             android.text.InputType.TYPE_CLASS_NUMBER or
                             android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val tilFat     = makeTil(getString(R.string.custom_food_fat_hint),
                             android.text.InputType.TYPE_CLASS_NUMBER or
                             android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val tilCarbs   = makeTil(getString(R.string.custom_food_carbs_hint),
                             android.text.InputType.TYPE_CLASS_NUMBER or
                             android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(hPad, vGap, hPad, 0)
            addView(tilName)
            addView(tilKcal)
            addView(tilProtein)
            addView(tilFat)
            addView(tilCarbs)
        }

        // Pre-fill if editing
        existing?.let {
            tilName.editText!!.setText(it.name)
            tilKcal.editText!!.setText(it.kcalPer100g.toString())
            tilProtein.editText!!.setText(it.proteinPer100g.toString())
            tilFat.editText!!.setText(it.fatPer100g.toString())
            tilCarbs.editText!!.setText(it.carbsPer100g.toString())
        }

        val title = if (existing == null) getString(R.string.custom_food_new)
                    else getString(R.string.custom_food_edit_title)

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(R.string.btn_save_food) { _, _ ->
                val name = tilName.editText!!.text.toString().trim()
                if (name.isEmpty()) {
                    tilName.error = getString(R.string.error_required)
                    return@setPositiveButton
                }
                tilName.error = null
                val food = CustomFood(
                    id             = existing?.id ?: java.util.UUID.randomUUID().toString(),
                    name           = name,
                    kcalPer100g    = tilKcal.editText!!.text.toString().toIntOrNull()     ?: 0,
                    proteinPer100g = tilProtein.editText!!.text.toString().toFloatOrNull() ?: 0f,
                    fatPer100g     = tilFat.editText!!.text.toString().toFloatOrNull()     ?: 0f,
                    carbsPer100g   = tilCarbs.editText!!.text.toString().toFloatOrNull()   ?: 0f
                )
                val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
                CustomFoodsRepository.save(prefs, food)
                Toast.makeText(this, getString(R.string.custom_food_saved), Toast.LENGTH_SHORT).show()
                refreshMyFoods()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteCustomFood(food: CustomFood) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.custom_food_delete_title)
            .setMessage(getString(R.string.custom_food_delete_message, food.name))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
                CustomFoodsRepository.delete(prefs, food.id)
                Toast.makeText(this, getString(R.string.custom_food_deleted), Toast.LENGTH_SHORT).show()
                refreshMyFoods()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun onQueryChanged(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            showState(State.HINT)
            return
        }
        showState(State.LOADING)
        searchJob = scope.launch {
            delay(500)
            val results = try {
                searchFoods(query)
            } catch (e: Exception) {
                null
            }
            when {
                results == null   -> showNoResults(getString(R.string.search_error))
                results.isEmpty() -> showNoResults(getString(R.string.search_no_results))
                else              -> showResults(results)
            }
        }
    }

    private suspend fun searchFoods(query: String): List<FoodSearchResult> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL(
                "https://world.openfoodfacts.org/cgi/search.pl" +
                "?search_terms=$encoded" +
                "&search_simple=1&action=process&json=1&page_size=25" +
                "&fields=product_name,brands,nutriments"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout    = 10_000
            conn.setRequestProperty("User-Agent", "MacroMax Android App")
            try {
                val body = conn.inputStream.bufferedReader().readText()
                parseResults(body)
            } finally {
                conn.disconnect()
            }
        }

    private fun parseResults(json: String): List<FoodSearchResult> {
        val products = JSONObject(json).getJSONArray("products")
        val results  = mutableListOf<FoodSearchResult>()
        for (i in 0 until products.length()) {
            val p    = products.getJSONObject(i)
            val name = p.optString("product_name").trim()
            if (name.isEmpty()) continue

            val n    = p.optJSONObject("nutriments") ?: continue
            val kcal = n.optDouble("energy-kcal_100g", -1.0)
            if (kcal < 0) continue

            results.add(
                FoodSearchResult(
                    name             = name,
                    brand            = p.optString("brands").split(",").first().trim(),
                    caloriesPer100g  = kcal.toInt(),
                    proteinPer100g   = n.optDouble("proteins_100g",       0.0).toFloat(),
                    fatPer100g       = n.optDouble("fat_100g",             0.0).toFloat(),
                    carbsPer100g     = n.optDouble("carbohydrates_100g",   0.0).toFloat()
                )
            )
            if (results.size >= 20) break
        }
        return results
    }

    // ── UI states ─────────────────────────────────────────────────────────────

    private enum class State { HINT, LOADING, RESULTS, NO_RESULTS }

    private fun showState(state: State) {
        tvSearchHint.visibility    = if (state == State.HINT)       View.VISIBLE else View.GONE
        progressSearch.visibility  = if (state == State.LOADING)    View.VISIBLE else View.GONE
        rvResults.visibility       = if (state == State.RESULTS)    View.VISIBLE else View.GONE
        layoutNoResults.visibility = if (state == State.NO_RESULTS) View.VISIBLE else View.GONE
    }

    private fun showResults(results: List<FoodSearchResult>) {
        rvResults.adapter = FoodSearchAdapter(results) { food -> showPortionDialog(food) }
        showState(State.RESULTS)
    }

    private fun showNoResults(message: String) {
        tvNoResults.text = message
        showState(State.NO_RESULTS)
    }

    // ── Portion dialog ────────────────────────────────────────────────────────

    private fun showPortionDialog(food: FoodSearchResult) {
        val dp = resources.displayMetrics.density
        val hPad = (24 * dp).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(hPad, 0, hPad, (8 * dp).toInt())
        }

        val tilGrams = TextInputLayout(this, null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            hint       = getString(R.string.hint_amount_g)
            suffixText = "g"
        }
        val etGrams = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        tilGrams.addView(etGrams)

        val cbFavourite = CheckBox(this).apply {
            text = getString(R.string.btn_save_favourite)
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }

        container.addView(tilGrams)
        container.addView(cbFavourite)

        MaterialAlertDialogBuilder(this)
            .setTitle(food.name)
            .setMessage(
                "${food.caloriesPer100g} kcal  ·  " +
                "P ${food.proteinPer100g.toInt()}g  " +
                "F ${food.fatPer100g.toInt()}g  " +
                "C ${food.carbsPer100g.toInt()}g" +
                "\n${getString(R.string.per_100g)}"
            )
            .setView(container)
            .setPositiveButton(R.string.btn_add) { _, _ ->
                val grams = etGrams.text.toString().toFloatOrNull()
                if (grams == null || grams <= 0f) {
                    Toast.makeText(this, getString(R.string.error_enter_amount), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val factor = grams / 100f
                val entry = FoodEntry(
                    name     = food.name,
                    calories = (food.caloriesPer100g * factor).toInt(),
                    proteinG = (food.proteinPer100g  * factor).toInt(),
                    fatG     = (food.fatPer100g      * factor).toInt(),
                    carbsG   = (food.carbsPer100g    * factor).toInt(),
                    mealType = selectedMealType()
                )
                saveEntry(entry)

                if (cbFavourite.isChecked) {
                    val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
                    FavouritesRepository.save(
                        prefs,
                        FavouriteFood(
                            name     = entry.name,
                            calories = entry.calories,
                            proteinG = entry.proteinG,
                            fatG     = entry.fatG,
                            carbsG   = entry.carbsG
                        )
                    )
                    Toast.makeText(this, getString(R.string.favourite_saved), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.food_saved), Toast.LENGTH_SHORT).show()
                }
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Manual entry dialog ───────────────────────────────────────────────────

    private fun showManualEntryDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_manual_food, null)
        val tilName  = view.findViewById<TextInputLayout>(R.id.tilManualName)
        val tilCal   = view.findViewById<TextInputLayout>(R.id.tilManualCalories)
        val etName   = view.findViewById<TextInputEditText>(R.id.etManualName)
        val etCal    = view.findViewById<TextInputEditText>(R.id.etManualCalories)
        val etPro    = view.findViewById<TextInputEditText>(R.id.etManualProtein)
        val etFat    = view.findViewById<TextInputEditText>(R.id.etManualFat)
        val etCarbs  = view.findViewById<TextInputEditText>(R.id.etManualCarbs)
        val cbFav    = view.findViewById<CheckBox>(R.id.cbSaveFavourite)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.btn_add_manually)
            .setView(view)
            .setPositiveButton(R.string.btn_save_food) { _, _ ->
                val name   = etName.text.toString().trim()
                val calStr = etCal.text.toString().trim()
                var valid  = true
                if (name.isEmpty())   { tilName.error = getString(R.string.error_required); valid = false } else tilName.error = null
                if (calStr.isEmpty()) { tilCal.error  = getString(R.string.error_required); valid = false } else tilCal.error  = null
                if (!valid) return@setPositiveButton

                val entry = FoodEntry(
                    name     = name,
                    calories = calStr.toIntOrNull()                         ?: 0,
                    proteinG = etPro.text.toString().trim().toIntOrNull()   ?: 0,
                    fatG     = etFat.text.toString().trim().toIntOrNull()   ?: 0,
                    carbsG   = etCarbs.text.toString().trim().toIntOrNull() ?: 0,
                    mealType = selectedMealType()
                )
                saveEntry(entry)

                if (cbFav.isChecked) {
                    val prefs = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
                    FavouritesRepository.save(
                        prefs,
                        FavouriteFood(
                            name     = entry.name,
                            calories = entry.calories,
                            proteinG = entry.proteinG,
                            fatG     = entry.fatG,
                            carbsG   = entry.carbsG
                        )
                    )
                    Toast.makeText(this, getString(R.string.favourite_saved), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.food_saved), Toast.LENGTH_SHORT).show()
                }
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Barcode lookup ────────────────────────────────────────────────────────

    private fun lookupBarcode(barcode: String) {
        showState(State.LOADING)
        searchJob?.cancel()
        searchJob = scope.launch {
            val food = try { fetchByBarcode(barcode) } catch (e: Exception) { null }
            if (food == null) {
                showNoResults(getString(R.string.barcode_not_found))
            } else {
                showState(State.HINT)
                showPortionDialog(food)
            }
        }
    }

    private suspend fun fetchByBarcode(barcode: String): FoodSearchResult? =
        withContext(Dispatchers.IO) {
            val url  = URL("https://world.openfoodfacts.org/api/v0/product/$barcode.json")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout    = 10_000
            conn.setRequestProperty("User-Agent", "MacroMax Android App")
            try {
                val body = conn.inputStream.bufferedReader().readText()
                val root = JSONObject(body)
                if (root.optInt("status", 0) == 0) return@withContext null

                val p = root.optJSONObject("product") ?: return@withContext null
                val name = p.optString("product_name").trim().ifEmpty { return@withContext null }
                val n    = p.optJSONObject("nutriments") ?: return@withContext null
                val kcal = n.optDouble("energy-kcal_100g", -1.0)
                if (kcal < 0) return@withContext null

                FoodSearchResult(
                    name            = name,
                    brand           = p.optString("brands").split(",").first().trim(),
                    caloriesPer100g = kcal.toInt(),
                    proteinPer100g  = n.optDouble("proteins_100g",     0.0).toFloat(),
                    fatPer100g      = n.optDouble("fat_100g",          0.0).toFloat(),
                    carbsPer100g    = n.optDouble("carbohydrates_100g",0.0).toFloat()
                )
            } finally {
                conn.disconnect()
            }
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun selectedMealType(): String = when (cgMealType.checkedChipId) {
        R.id.chipBreakfast -> "breakfast"
        R.id.chipLunch     -> "lunch"
        R.id.chipDinner    -> "dinner"
        R.id.chipSnack     -> "snack"
        else               -> "other"
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun saveEntry(entry: FoodEntry) {
        val prefs  = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val logKey = "food_log_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val arr    = JSONArray(prefs.getString(logKey, "[]") ?: "[]")
        arr.put(JSONObject().apply {
            put("name", entry.name)
            put("cal",  entry.calories)
            put("pro",  entry.proteinG)
            put("fat",  entry.fatG)
            put("car",  entry.carbsG)
            put("meal", entry.mealType)
        })
        prefs.edit().putString(logKey, arr.toString()).apply()
    }
}
