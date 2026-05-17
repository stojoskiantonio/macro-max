package com.example.macromax

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryDayAdapter(private val days: List<DayHistory>) :
    RecyclerView.Adapter<HistoryDayAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView          = view.findViewById(R.id.tvHistoryDate)
        val tvTotalCal: TextView      = view.findViewById(R.id.tvHistoryTotalCal)
        val tvMacros: TextView        = view.findViewById(R.id.tvHistoryMacros)
        val foodContainer: LinearLayout = view.findViewById(R.id.containerFoodItems)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_history_day, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val day = days[position]

        holder.tvDate.text     = day.displayDate
        holder.tvTotalCal.text = "${day.totalCalories} kcal"
        holder.tvMacros.text   =
            "P ${day.totalProtein}g  ·  F ${day.totalFat}g  ·  C ${day.totalCarbs}g"

        // Populate food item rows
        holder.foodContainer.removeAllViews()
        val inflater = LayoutInflater.from(holder.itemView.context)
        for (entry in day.entries) {
            val row = inflater.inflate(
                R.layout.item_history_food_row, holder.foodContainer, false
            )
            row.findViewById<TextView>(R.id.tvHistoryFoodName).text = entry.name
            row.findViewById<TextView>(R.id.tvHistoryFoodCal).text  = "${entry.calories} kcal"
            holder.foodContainer.addView(row)
        }
    }

    override fun getItemCount() = days.size
}
