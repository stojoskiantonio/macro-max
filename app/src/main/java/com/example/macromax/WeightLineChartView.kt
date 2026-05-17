package com.example.macromax

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.ColorUtils

class WeightLineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class WeightPoint(
        val label: String,   // short date label e.g. "18 May"
        val weightKg: Float,
        val isToday: Boolean
    )

    var points: List<WeightPoint> = emptyList()
        set(value) { field = value; invalidate() }

    private val dp = context.resources.displayMetrics.density
    private val sp = context.resources.displayMetrics.scaledDensity

    private val onSurface: Int = run {
        val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
        val c = ta.getColor(0, Color.WHITE)
        ta.recycle()
        c
    }

    private val primaryColor: Int = run {
        val tv = TypedValue()
        context.theme.resolveAttribute(android.R.attr.colorPrimary, tv, true)
        tv.data
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = primaryColor
        strokeWidth = 2.5f * dp
        style       = Paint.Style.STROKE
        strokeJoin  = Paint.Join.ROUND
        strokeCap   = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = null // set in onDraw once size is known
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        style = Paint.Style.FILL
    }

    private val dotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = primaryColor
        style       = Paint.Style.STROKE
        strokeWidth = 2f * dp
    }

    private val todayDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = ColorUtils.setAlphaComponent(onSurface, 0x70)
        textSize  = 9f * sp
        textAlign = Paint.Align.CENTER
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = ColorUtils.setAlphaComponent(onSurface, 0xCC)
        textSize  = 9f * sp
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.DEFAULT_BOLD
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = ColorUtils.setAlphaComponent(onSurface, 0x12)
        strokeWidth = 1f * dp
        style       = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        val n = points.size
        if (n == 0) return

        val w = width.toFloat()
        val h = height.toFloat()

        val labelH  = 18f * dp
        val topPad  = 20f * dp   // space above for value labels
        val chartH  = h - labelH - topPad
        val leftPad = 8f * dp
        val rightPad = 8f * dp

        val weights = points.map { it.weightKg }
        val minW    = weights.min()
        val maxW    = weights.max()
        val range   = (maxW - minW).let { if (it < 1f) 2f else it }
        val yMin    = minW - range * 0.15f
        val yMax    = maxW + range * 0.15f

        fun xOf(i: Int): Float {
            return if (n == 1) w / 2f
            else leftPad + (w - leftPad - rightPad) * i / (n - 1).toFloat()
        }

        fun yOf(kg: Float): Float {
            val fraction = (kg - yMin) / (yMax - yMin)
            return topPad + chartH * (1f - fraction)
        }

        // Subtle horizontal grid lines (3 lines)
        for (k in 0..2) {
            val gy = topPad + chartH * k / 2f
            canvas.drawLine(leftPad, gy, w - rightPad, gy, gridPaint)
        }

        // Fill gradient under line
        if (n > 1) {
            val path = Path()
            path.moveTo(xOf(0), yOf(points[0].weightKg))
            for (i in 1 until n) path.lineTo(xOf(i), yOf(points[i].weightKg))
            path.lineTo(xOf(n - 1), topPad + chartH)
            path.lineTo(xOf(0), topPad + chartH)
            path.close()
            fillPaint.shader = LinearGradient(
                0f, topPad, 0f, topPad + chartH,
                ColorUtils.setAlphaComponent(primaryColor, 0x40),
                ColorUtils.setAlphaComponent(primaryColor, 0x00),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(path, fillPaint)
        }

        // Line
        if (n > 1) {
            val linePath = Path()
            linePath.moveTo(xOf(0), yOf(points[0].weightKg))
            for (i in 1 until n) linePath.lineTo(xOf(i), yOf(points[i].weightKg))
            canvas.drawPath(linePath, linePaint)
        }

        // Dots and labels — only show date label every Nth point to avoid crowding
        val labelEvery = when {
            n <= 7  -> 1
            n <= 14 -> 2
            n <= 30 -> 5
            else    -> 7
        }

        for (i in points.indices) {
            val pt = points[i]
            val x  = xOf(i)
            val y  = yOf(pt.weightKg)

            if (pt.isToday) {
                // Large filled dot with white centre
                canvas.drawCircle(x, y, 7f * dp, todayDotPaint)
                val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = onSurface.let {
                        // Use surface color for inner dot
                        val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
                        val c = ta.getColor(0, Color.BLACK)
                        ta.recycle()
                        c
                    }
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(x, y, 3.5f * dp, innerPaint)
            } else {
                // Small filled dot
                canvas.drawCircle(x, y, 4f * dp, dotPaint)
            }

            // Value label above first, last, and today's point
            if (pt.isToday || i == 0 || i == n - 1) {
                val label = String.format("%.1f", pt.weightKg)
                canvas.drawText(label, x, y - 10f * dp, valuePaint)
            }

            // Date label below chart area
            if (i == 0 || i == n - 1 || pt.isToday || i % labelEvery == 0) {
                canvas.drawText(pt.label, x, h - 3f * dp, labelPaint)
            }
        }
    }
}
