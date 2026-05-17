package com.example.macromax

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FoodSearchAdapter(
    private val items: List<FoodSearchResult>,
    private val onItemClick: (FoodSearchResult) -> Unit
) : RecyclerView.Adapter<FoodSearchAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView     = view.findViewById(R.id.tvSearchFoodName)
        val tvBrand: TextView    = view.findViewById(R.id.tvSearchBrand)
        val tvCalories: TextView = view.findViewById(R.id.tvSearchCalories)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_food_search, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text     = item.name
        holder.tvBrand.text    = item.brand.ifEmpty { "Generic" }
        holder.tvCalories.text = item.caloriesPer100g.toString()
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}
