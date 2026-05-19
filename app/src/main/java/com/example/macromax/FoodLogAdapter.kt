package com.example.macromax

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class FoodLogItem {
    data class Header(
        val mealType: String,
        val calConsumed: Int = 0,
        val calTarget: Int = 0
    ) : FoodLogItem()
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

        /**
         * Build a grouped FoodLogItem list from a flat indexed list of entries.
         * [mealTargets] is an optional map of mealType → calorie target to show
         * progress in each section header.
         */
        fun buildItems(
            entries: List<FoodEntry>,
            mealTargets: Map<String, Int> = emptyMap()
        ): List<FoodLogItem> {
            val order   = listOf("breakfast", "lunch", "dinner", "snack", "other")
            val indexed = entries.mapIndexed { i, e -> i to e }
            val grouped = indexed.groupBy { (_, e) -> e.mealType.lowercase().ifBlank { "other" } }

            return buildList {
                for (mealType in order) {
                    val group = grouped[mealType] ?: continue
                    val consumed = group.sumOf { (_, e) -> e.calories }
                    val target   = mealTargets[mealType] ?: 0
                    add(FoodLogItem.Header(mealType, consumed, target))
                    group.forEach { (rawIndex, food) -> add(FoodLogItem.Entry(rawIndex, food)) }
                }
            }
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHeader: TextView   = view.findViewById(R.id.tvMealHeader)
        val tvCalories: TextView = view.findViewById(R.id.tvMealCalories)
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
                (holder as HeaderViewHolder).apply {
                    tvHeader.text = mealLabel(itemView.context, item.mealType)
                    if (item.calTarget > 0) {
                        tvCalories.text      = "${item.calConsumed} / ${item.calTarget} kcal"
                        tvCalories.visibility = View.VISIBLE
                    } else {
                        tvCalories.visibility = View.GONE
                    }
                }
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
