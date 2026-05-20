package com.example.macromax

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CustomFoodAdapter(
    private val items: List<CustomFood>,
    private val onAdd: (CustomFood) -> Unit,
    private val onEdit: (CustomFood) -> Unit,
    private val onDelete: (CustomFood) -> Unit
) : RecyclerView.Adapter<CustomFoodAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName:   TextView    = view.findViewById(R.id.tvCustomFoodName)
        val tvMacros: TextView    = view.findViewById(R.id.tvCustomFoodMacros)
        val btnAdd:   ImageButton = view.findViewById(R.id.btnCustomFoodAdd)
        val btnEdit:  ImageButton = view.findViewById(R.id.btnCustomFoodEdit)
        val btnDel:   ImageButton = view.findViewById(R.id.btnCustomFoodDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_custom_food, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val f = items[position]
        holder.tvName.text   = f.name
        holder.tvMacros.text =
            "${f.kcalPer100g} kcal  ·  P ${f.proteinPer100g.toInt()}g  " +
            "F ${f.fatPer100g.toInt()}g  C ${f.carbsPer100g.toInt()}g  / 100g"
        holder.btnAdd.setOnClickListener  { onAdd(f)    }
        holder.btnEdit.setOnClickListener { onEdit(f)   }
        holder.btnDel.setOnClickListener  { onDelete(f) }
    }
}
