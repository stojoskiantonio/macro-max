package com.example.macromax

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecipeAdapter(
    private val items: List<Recipe>,
    private val onTap: (Recipe) -> Unit,
    private val onDelete: ((Recipe) -> Unit)? = null
) : RecyclerView.Adapter<RecipeAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName:        TextView    = view.findViewById(R.id.tvRecipeName)
        val tvMacros:      TextView    = view.findViewById(R.id.tvRecipeMacros)
        val tvIngCount:    TextView    = view.findViewById(R.id.tvRecipeIngCount)
        val btnDelete:     ImageButton = view.findViewById(R.id.btnRecipeDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_recipe, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.tvName.text     = r.name
        holder.tvMacros.text   =
            "${r.calPerServing} kcal  ·  P ${r.protPerServing}g  F ${r.fatPerServing}g  C ${r.carbPerServing}g"
        holder.tvIngCount.text = holder.itemView.context.resources.getQuantityString(
            R.plurals.recipe_ingredients_count, r.ingredients.size, r.ingredients.size
        )

        if (onDelete != null) {
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnDelete.setOnClickListener { onDelete.invoke(r) }
        } else {
            holder.btnDelete.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onTap(r) }
    }
}
