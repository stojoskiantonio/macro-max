package com.example.macromax

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class WeeklyCalorieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class DayBar(
        val label: String,
        val consumed: Int,
        val isToday: Boolean
    )

    var bars: List<DayBar> = emptyList()
        set(value) { field = value; invalidate() }

    var target: Int = 0
        set(value) { field = value; invalidate() }

    private val dp  = context.resources.displayMetrics.density
    private val sp  = context.resources.displayMetrics.scaledDensity

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val targetLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = 0xAAFFFFFF.toInt()
        strokeWidth = 1.5f * dp
        style       = Paint.Style.STROKE
        pathEffect  = DashPathEffect(floatArrayOf(6f * dp, 4f * dp), 0f)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = 0x70FFFFFF.toInt()
        textSize  = 10f * sp
        textAlign = Paint.Align.CENTER
    }

    private val todayLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = 0xCCFFFFFF.toInt()
        textSize  = 10f * sp
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.DEFAULT_BOLD
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize  = 9f * sp
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        val n = bars.size
        if (n == 0) return

        val w = width.toFloat()
        val h = height.toFloat()

        val labelH = 18f * dp
        val valueH = 15f * dp
        val topPad = 6f  * dp
        val chartH = h - labelH - valueH - topPad

        val maxConsumed = bars.maxOf { it.consumed }
        val maxVal      = maxOf(target, maxConsumed, 1).toFloat()

        // Spacing: 35% of width is gaps, 65% is bars
        val totalGapW = w * 0.35f
        val gapUnit   = totalGapW / (n + 1)
        val barW      = (w - totalGapW) / n

        // Target dashed line
        if (target > 0) {
            val ty = topPad + valueH + chartH * (1f - target / maxVal)
            canvas.drawLine(0f, ty, w, ty, targetLinePaint)
        }

        bars.forEachIndexed { i, day ->
            val left  = gapUnit * (i + 1) + barW * i
            val right = left + barW
            val cx    = left + barW / 2f

            val fraction = if (day.consumed > 0) day.consumed / maxVal else 0f
            val barH  = chartH * fraction
            val top   = topPad + valueH + chartH - barH
            val bot   = topPad + valueH + chartH

            // Bar colour
            val baseColor = when {
                day.consumed == 0 -> 0x18FFFFFF
                target == 0       -> 0xFF64B5F6.toInt()
                day.consumed >= target * 0.9f &&
                day.consumed <= target * 1.1f -> 0xFF4CAF50.toInt()
                day.consumed > target * 1.1f  -> 0xFFEF5350.toInt()
                else              -> 0xFF64B5F6.toInt()
            }
            barPaint.color = baseColor
            // Dim past days slightly
            if (!day.isToday && day.consumed > 0) {
                barPaint.alpha = (barPaint.alpha * 0.72f).toInt().coerceIn(0, 255)
            }

            if (barH > 0f) {
                canvas.drawRoundRect(
                    RectF(left, top, right, bot),
                    4f * dp, 4f * dp,
                    barPaint
                )
            } else {
                // Empty day: faint thin line at baseline
                barPaint.color = 0x18FFFFFF
                canvas.drawRoundRect(
                    RectF(left, bot - 3f * dp, right, bot),
                    2f * dp, 2f * dp, barPaint
                )
            }

            // Value label above bar
            if (day.consumed > 0) {
                valuePaint.color = if (day.isToday) 0xBBFFFFFF.toInt() else 0x60FFFFFF.toInt()
                val label = if (day.consumed >= 1000) {
                    String.format("%.1fk", day.consumed / 1000f)
                } else {
                    day.consumed.toString()
                }
                canvas.drawText(label, cx, top - 3f * dp, valuePaint)
            }

            // Day label
            canvas.drawText(
                day.label,
                cx,
                h - 2f * dp,
                if (day.isToday) todayLabelPaint else labelPaint
            )
        }
    }
}
