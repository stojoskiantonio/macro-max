package com.example.macromax

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FoodLogAdapter(
    private val items: List<FoodEntry>,
    private val onEditClick: (index: Int, entry: FoodEntry) -> Unit,
    private val onDeleteClick: (index: Int) -> Unit
) : RecyclerView.Adapter<FoodLogAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView      = view.findViewById(R.id.tvFoodName)
        val tvMacros: TextView    = view.findViewById(R.id.tvFoodMacros)
        val tvCalories: TextView  = view.findViewById(R.id.tvFoodCalories)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteEntry)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_food_log, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text     = item.name
        holder.tvMacros.text   = "P ${item.proteinG}g  ·  F ${item.fatG}g  ·  C ${item.carbsG}g"
        holder.tvCalories.text = "${item.calories} kcal"

        holder.itemView.setOnClickListener { onEditClick(position, item) }
        holder.btnDelete.setOnClickListener { onDeleteClick(position) }
    }

    override fun getItemCount() = items.size
}
