package com.example.macromax

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar

class RecipeListActivity : AppCompatActivity() {

    private lateinit var rvRecipes:     RecyclerView
    private lateinit var tvEmpty:       TextView
    private lateinit var fab:           ExtendedFloatingActionButton

    private val prefs get() = getSharedPreferences("macromax_prefs", MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_list)

        findViewById<ImageButton>(R.id.btnRecipeListBack).setOnClickListener { finish() }

        rvRecipes = findViewById(R.id.rvRecipes)
        tvEmpty   = findViewById(R.id.tvRecipesEmpty)
        fab       = findViewById(R.id.fabNewRecipe)

        rvRecipes.layoutManager = LinearLayoutManager(this)

        fab.setOnClickListener {
            startActivity(Intent(this, RecipeEditActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadRecipes()
    }

    private fun loadRecipes() {
        val list = RecipeRepository.load(prefs)
        if (list.isEmpty()) {
            rvRecipes.visibility = View.GONE
            tvEmpty.visibility   = View.VISIBLE
        } else {
            tvEmpty.visibility   = View.GONE
            rvRecipes.visibility = View.VISIBLE
            rvRecipes.adapter = RecipeAdapter(
                items    = list,
                onTap    = { recipe ->
                    val intent = Intent(this, RecipeEditActivity::class.java)
                    intent.putExtra(RecipeEditActivity.EXTRA_RECIPE_ID, recipe.id)
                    startActivity(intent)
                },
                onDelete = { recipe -> confirmDelete(recipe) }
            )
        }
    }

    private fun confirmDelete(recipe: Recipe) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recipe_delete_title)
            .setMessage(getString(R.string.recipe_delete_message, recipe.name))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                RecipeRepository.delete(prefs, recipe.id)
                Snackbar.make(fab, getString(R.string.recipe_deleted), Snackbar.LENGTH_SHORT).show()
                loadRecipes()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
