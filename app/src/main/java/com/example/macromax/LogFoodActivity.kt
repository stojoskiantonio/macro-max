package com.example.macromax

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
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

    private lateinit var rvResults: RecyclerView
    private lateinit var progressSearch: CircularProgressIndicator
    private lateinit var tvSearchHint: TextView
    private lateinit var layoutNoResults: View
    private lateinit var tvNoResults: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_food)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        rvResults      = findViewById(R.id.rvSearchResults)
        progressSearch = findViewById(R.id.progressSearch)
        tvSearchHint   = findViewById(R.id.tvSearchHint)
        layoutNoResults = findViewById(R.id.layoutNoResults)
        tvNoResults    = findViewById(R.id.tvNoResults)

        rvResults.layoutManager = LinearLayoutManager(this)

        // Live search with 500 ms debounce
        findViewById<TextInputEditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                onQueryChanged(query)
            }
        })

        // Manual entry fallback
        findViewById<MaterialButton>(R.id.btnManualEntry).setOnClickListener {
            showManualEntryDialog()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ── Search ───────────────────────────────────────────────────────────────

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
                results == null      -> showNoResults(getString(R.string.search_error))
                results.isEmpty()    -> showNoResults(getString(R.string.search_no_results))
                else                 -> showResults(results)
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
        val tilGrams = TextInputLayout(this, null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            hint      = getString(R.string.hint_amount_g)
            suffixText = "g"
            setPadding(
                (24 * resources.displayMetrics.density).toInt(), 0,
                (24 * resources.displayMetrics.density).toInt(), 0
            )
        }
        val etGrams = TextInputEditText(this).apply {
            inputType  = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        tilGrams.addView(etGrams)

        MaterialAlertDialogBuilder(this)
            .setTitle(food.name)
            .setMessage(
                "${food.caloriesPer100g} kcal  ·  " +
                "P ${food.proteinPer100g.toInt()}g  " +
                "F ${food.fatPer100g.toInt()}g  " +
                "C ${food.carbsPer100g.toInt()}g" +
                "\n${getString(R.string.per_100g)}"
            )
            .setView(tilGrams)
            .setPositiveButton(R.string.btn_add) { _, _ ->
                val grams = etGrams.text.toString().toFloatOrNull()
                if (grams == null || grams <= 0f) {
                    Toast.makeText(this, getString(R.string.error_enter_amount), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val factor = grams / 100f
                saveEntry(
                    FoodEntry(
                        name      = food.name,
                        calories  = (food.caloriesPer100g * factor).toInt(),
                        proteinG  = (food.proteinPer100g  * factor).toInt(),
                        fatG      = (food.fatPer100g      * factor).toInt(),
                        carbsG    = (food.carbsPer100g    * factor).toInt()
                    )
                )
                Toast.makeText(this, getString(R.string.food_saved), Toast.LENGTH_SHORT).show()
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

                saveEntry(
                    FoodEntry(
                        name      = name,
                        calories  = calStr.toIntOrNull()                               ?: 0,
                        proteinG  = etPro.text.toString().trim().toIntOrNull()         ?: 0,
                        fatG      = etFat.text.toString().trim().toIntOrNull()         ?: 0,
                        carbsG    = etCarbs.text.toString().trim().toIntOrNull()       ?: 0
                    )
                )
                Toast.makeText(this, getString(R.string.food_saved), Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        })
        prefs.edit().putString(logKey, arr.toString()).apply()
    }
}
