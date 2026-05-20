package com.example.macromax

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils

class HourlyBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 24-element array: value for each hour 0–23 */
    var data: IntArray = IntArray(24)
        set(value) {
            field = if (value.size >= 24) value else IntArray(24).also { dst ->
                value.copyInto(dst, endIndex = minOf(value.size, 24))
            }
            invalidate()
        }

    var barColor: Int = Color.parseColor("#CE93D8")
        set(value) { field = value; barPaint.color = value; invalidate() }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#CE93D8")
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = ColorUtils.setAlphaComponent(Color.GRAY, 160)
        textAlign = Paint.Align.CENTER
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = ColorUtils.setAlphaComponent(Color.GRAY, 30)
        strokeWidth = dp(0.5f)
        style       = Paint.Style.STROKE
    }

    // Label positions: show "00", "06", "12", "18"
    private val labelHours = listOf(0, 6, 12, 18)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        labelPaint.textSize = sp(9f)
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        labelPaint.textSize = sp(9f)

        val w      = width.toFloat()
        val h      = height.toFloat()
        val labelH = (labelPaint.descent() - labelPaint.ascent()) + dp(3f)
        val chartH = h - labelH

        val maxVal  = data.maxOrNull()?.takeIf { it > 0 } ?: 1
        val slotW   = w / 24f
        val barW    = (slotW * 0.55f).coerceAtLeast(dp(2f))
        val cornerR = dp(1.5f)

        // Faint horizontal grid lines
        val gridY1 = chartH * 0.33f
        val gridY2 = chartH * 0.66f
        canvas.drawLine(0f, gridY1, w, gridY1, gridPaint)
        canvas.drawLine(0f, gridY2, w, gridY2, gridPaint)

        // Bars
        for (hour in 0 until 24) {
            val value  = data[hour]
            val barH   = (value.toFloat() / maxVal) * (chartH - dp(2f))
            val cx     = hour * slotW + slotW / 2f
            val left   = cx - barW / 2f
            val right  = cx + barW / 2f
            val top    = chartH - barH
            val bottom = chartH - dp(0.5f)
            if (barH >= dp(1f)) {
                canvas.drawRoundRect(left, top, right, bottom, cornerR, cornerR, barPaint)
            } else {
                // Draw a tiny stub so users can see the axis exists
                canvas.drawRect(left, chartH - dp(1f), right, bottom, barPaint.apply {
                    alpha = 60
                })
                barPaint.alpha = 255
            }
        }

        // Time labels
        val labelY = h - labelPaint.descent()
        for (lh in labelHours) {
            val x = lh * slotW + slotW / 2f
            canvas.drawText(String.format("%02d", lh), x, labelY, labelPaint)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity
}
