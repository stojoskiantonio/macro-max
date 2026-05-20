package com.example.macromax

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ActivityHistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activity_history)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        buildHistory()
    }

    private fun buildHistory() {
        val prefs     = getSharedPreferences("macromax_prefs", MODE_PRIVATE)
        val container = findViewById<LinearLayout>(R.id.containerActivityHistory)
        val empty     = findViewById<TextView>(R.id.tvActivityHistoryEmpty)
        val keyFmt    = SimpleDateFormat("yyyyMMdd", Locale.US)
        val cal       = Calendar.getInstance()
        val dp        = resources.displayMetrics.density
        var hasAny    = false

        container.removeAllViews()

        for (daysBack in 0 until 60) {
            if (daysBack > 0) cal.add(Calendar.DAY_OF_YEAR, -1)
            val dateKey  = keyFmt.format(cal.time)
            val steps    = prefs.getInt("steps_$dateKey", 0)
            val workouts = WorkoutRepository.load(prefs, dateKey)
            val stepCal  = (steps * 0.04).toInt()
            val wrkCal   = workouts.sumOf { it.caloriesBurned }
            val totalBurned = stepCal + wrkCal

            if (steps == 0 && workouts.isEmpty()) continue
            hasAny = true

            // Day card
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                radius        = 14 * dp
                cardElevation = 0f
                layoutParams  = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = (10 * dp).toInt() }
            }

            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    (16 * dp).toInt(), (14 * dp).toInt(),
                    (16 * dp).toInt(), (14 * dp).toInt()
                )
            }

            // ── Date header ──────────────────────────────────────────────────
            val isToday     = (daysBack == 0)
            val isYesterday = (daysBack == 1)
            val dayFmt      = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
            val dateLabel   = when {
                isToday     -> "Today · ${dayFmt.format(cal.time)}"
                isYesterday -> "Yesterday · ${dayFmt.format(cal.time)}"
                else        -> dayFmt.format(cal.time)
            }

            val tvDate = TextView(this).apply {
                text      = dateLabel
                textSize  = 12f
                alpha     = if (isToday) 0.9f else 0.5f
                if (isToday) setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = (10 * dp).toInt() }
            }
            inner.addView(tvDate)

            // ── Steps row ────────────────────────────────────────────────────
            if (steps > 0) {
                inner.addView(buildRow(
                    icon  = "👟",
                    title = "%,d steps".format(steps),
                    sub   = "${stepCal} kcal burned",
                    color = Color.parseColor("#CE93D8")
                ))
            }

            // ── Workout rows ─────────────────────────────────────────────────
            for (w in workouts) {
                inner.addView(divider())
                inner.addView(buildRow(
                    icon  = workoutIcon(w.exerciseType),
                    title = "${w.exerciseName}  ·  ${w.durationMinutes} min",
                    sub   = "${w.caloriesBurned} kcal burned",
                    color = Color.parseColor("#F44336")
                ))
            }

            // ── Total burned footer ───────────────────────────────────────────
            if (workouts.isNotEmpty() || stepCal > 0) {
                val tvTotal = TextView(this).apply {
                    text     = "Total burned: $totalBurned kcal"
                    textSize = 12f
                    alpha    = 0.45f
                    gravity  = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.topMargin = (10 * dp).toInt() }
                }
                inner.addView(tvTotal)
            }

            card.addView(inner)
            container.addView(card)
        }

        empty.visibility = if (hasAny) View.GONE else View.VISIBLE
    }

    private fun buildRow(icon: String, title: String, sub: String, color: Int): View {
        val dp  = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.topMargin    = (6 * dp).toInt()
                it.bottomMargin = (2 * dp).toInt()
            }
        }

        val tvIcon = TextView(this).apply {
            text      = icon
            textSize  = 18f
            gravity   = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                (32 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val textCol = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        textCol.addView(TextView(this).apply {
            text      = title
            textSize  = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(resolveOnSurface())
        })
        textCol.addView(TextView(this).apply {
            text      = sub
            textSize  = 11f
            alpha     = 0.55f
            setTextColor(color)
        })

        row.addView(tvIcon)
        row.addView(textCol)
        return row
    }

    private fun divider(): View {
        val dp = resources.displayMetrics.density
        return View(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@ActivityHistoryActivity,
                R.color.divider_line))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also {
                it.topMargin    = (8 * dp).toInt()
                it.bottomMargin = (2 * dp).toInt()
            }
        }
    }

    private fun workoutIcon(type: String) = when (type) {
        "running"   -> "🏃"
        "walking"   -> "🚶"
        "cycling"   -> "🚴"
        "swimming"  -> "🏊"
        "weights"   -> "🏋️"
        "hiit"      -> "⚡"
        "jump_rope" -> "🪢"
        "yoga"      -> "🧘"
        else        -> "💪"
    }

    private fun resolveOnSurface(): Int {
        val ta = obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
        val c  = ta.getColor(0, Color.BLACK)
        ta.recycle()
        return c
    }
}
