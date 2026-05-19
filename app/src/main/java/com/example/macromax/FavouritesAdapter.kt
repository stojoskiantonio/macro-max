package com.example.macromax

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FavouritesAdapter(
    private val items: List<FavouriteFood>,
    private val onAdd: (FavouriteFood) -> Unit,
    private val onDelete: (FavouriteFood) -> Unit
) : RecyclerView.Adapter<FavouritesAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName:   TextView    = view.findViewById(R.id.tvFavName)
        val tvMacros: TextView    = view.findViewById(R.id.tvFavMacros)
        val btnAdd:   ImageButton = view.findViewById(R.id.btnFavAdd)
        val btnDel:   ImageButton = view.findViewById(R.id.btnFavDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_favourite, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val f = items[position]
        holder.tvName.text   = f.name
        holder.tvMacros.text = "${f.calories} kcal  ·  P ${f.proteinG}g  F ${f.fatG}g  C ${f.carbsG}g"
        holder.btnAdd.setOnClickListener  { onAdd(f) }
        holder.btnDel.setOnClickListener  { onDelete(f) }
    }
}
