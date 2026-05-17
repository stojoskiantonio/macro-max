package com.example.macromax

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class StepDonutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 0.0 – 1.0 fill of the arc (steps / goal) */
    var progress: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    var centerText: String = "0"
        set(value) { field = value; invalidate() }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = dp(13f)
        color       = Color.parseColor("#1AFFFFFF")
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = dp(13f)
        color       = Color.parseColor("#FF9800") // orange
        strokeCap   = Paint.Cap.ROUND
    }

    private val calPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize  = sp(18f)
        typeface  = Typeface.DEFAULT_BOLD
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#99FFFFFF")
        textAlign = Paint.Align.CENTER
        textSize  = sp(9f)
    }

    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        val cx     = width  / 2f
        val cy     = height / 2f
        val sw     = dp(13f)
        val radius = minOf(cx, cy) - sw / 2f - dp(6f)
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        canvas.drawCircle(cx, cy, radius, trackPaint)

        if (progress > 0f) {
            canvas.drawArc(oval, -90f, progress * 360f, false, arcPaint)
        }

        val calText  = centerText
        val label    = "kcal"
        val calH     = calPaint.descent()   - calPaint.ascent()
        val labelH   = labelPaint.descent() - labelPaint.ascent()
        val gap      = dp(3f)
        val groupH   = calH + gap + labelH
        val topY     = cy - groupH / 2f

        canvas.drawText(calText, cx, topY + calH   - calPaint.descent(),   calPaint)
        canvas.drawText(label,   cx, topY + calH + gap + labelH - labelPaint.descent(), labelPaint)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity
}
