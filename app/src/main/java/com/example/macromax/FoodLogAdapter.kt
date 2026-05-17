package com.example.macromax

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class FoodLogItem {
    data class Header(val mealType: String) : FoodLogItem()
    data class Entry(val rawIndex: Int, val food: FoodEntry) : FoodLogItem()
}

class FoodLogAdapter(
    private val items: List<FoodLogItem>,
    private val onEditClick: (rawIndex: Int, entry: FoodEntry) -> Unit,
    private val onDeleteClick: (rawIndex: Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY  = 1

        fun mealLabel(context: Context, mealType: String) = when (mealType) {
            "breakfast" -> context.getString(R.string.meal_breakfast)
            "lunch"     -> context.getString(R.string.meal_lunch)
            "dinner"    -> context.getString(R.string.meal_dinner)
            "snack"     -> context.getString(R.string.meal_snack)
            else        -> context.getString(R.string.meal_other)
        }

        /** Build a grouped FoodLogItem list from a flat indexed list of entries. */
        fun buildItems(entries: List<FoodEntry>): List<FoodLogItem> {
            val order   = listOf("breakfast", "lunch", "dinner", "snack", "other")
            val indexed = entries.mapIndexed { i, e -> i to e }
            val grouped = indexed.groupBy { (_, e) -> e.mealType.lowercase().ifBlank { "other" } }

            return buildList {
                for (mealType in order) {
                    val group = grouped[mealType] ?: continue
                    add(FoodLogItem.Header(mealType))
                    group.forEach { (rawIndex, food) -> add(FoodLogItem.Entry(rawIndex, food)) }
                }
            }
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHeader: TextView = view.findViewById(R.id.tvMealHeader)
    }

    class EntryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView       = view.findViewById(R.id.tvFoodName)
        val tvMacros: TextView     = view.findViewById(R.id.tvFoodMacros)
        val tvCalories: TextView   = view.findViewById(R.id.tvFoodCalories)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteEntry)
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is FoodLogItem.Header -> TYPE_HEADER
        is FoodLogItem.Entry  -> TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(inf.inflate(R.layout.item_meal_header, parent, false))
            else        -> EntryViewHolder(inf.inflate(R.layout.item_food_log, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is FoodLogItem.Header -> {
                (holder as HeaderViewHolder).tvHeader.text =
                    mealLabel(holder.itemView.context, item.mealType)
            }
            is FoodLogItem.Entry -> {
                (holder as EntryViewHolder).apply {
                    tvName.text     = item.food.name
                    tvMacros.text   = "P ${item.food.proteinG}g  ·  F ${item.food.fatG}g  ·  C ${item.food.carbsG}g"
                    tvCalories.text = "${item.food.calories} kcal"
                    itemView.setOnClickListener { onEditClick(item.rawIndex, item.food) }
                    btnDelete.setOnClickListener { onDeleteClick(item.rawIndex) }
                }
            }
        }
    }

    override fun getItemCount() = items.size
}
