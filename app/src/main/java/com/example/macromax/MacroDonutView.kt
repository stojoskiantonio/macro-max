package com.example.macromax

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils

class MacroDonutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var proteinG: Int = 0
        set(value) { field = value; invalidate() }
    var fatG: Int = 0
        set(value) { field = value; invalidate() }
    var carbG: Int = 0
        set(value) { field = value; invalidate() }
    var totalCalories: Int = 0
        set(value) { field = value; invalidate() }
    var targetCalories: Int = 0
        set(value) { field = value; invalidate() }

    // Resolve colorOnSurface from the current theme so text adapts to light/dark
    private val onSurface: Int = run {
        val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
        val c  = ta.getColor(0, Color.WHITE)
        ta.recycle()
        c
    }

    private val strokeW = dp(13f)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = dp(13f)
        color       = ColorUtils.setAlphaComponent(onSurface, 26)   // ~10% opacity
    }

    private fun arcPaint(hex: String) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = dp(13f)
        color       = Color.parseColor(hex)
        strokeCap   = Paint.Cap.BUTT
    }

    private val proteinPaint = arcPaint("#4CAF50") // green
    private val fatPaint     = arcPaint("#EF5350") // red
    private val carbPaint    = arcPaint("#64B5F6") // blue

    private val calPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = onSurface
        textAlign = Paint.Align.CENTER
        textSize  = sp(18f)
        typeface  = Typeface.DEFAULT_BOLD
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = ColorUtils.setAlphaComponent(onSurface, 0x99)   // ~60% opacity
        textAlign = Paint.Align.CENTER
        textSize  = sp(9f)
    }

    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        val cx     = width  / 2f
        val cy     = height / 2f
        val radius = minOf(cx, cy) - strokeW / 2f - dp(6f)
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        canvas.drawCircle(cx, cy, radius, trackPaint)

        val proteinCal = proteinG * 4f
        val fatCal     = fatG     * 9f
        val carbCal    = carbG    * 4f
        val total      = proteinCal + fatCal + carbCal

        if (total > 0f) {
            val gap           = 4f
            val proteinSweep  = (proteinCal / total) * 360f
            val fatSweep      = (fatCal     / total) * 360f
            val carbSweep     = (carbCal    / total) * 360f
            var start         = -90f

            if (proteinSweep > gap) canvas.drawArc(oval, start + gap / 2f, proteinSweep - gap, false, proteinPaint)
            start += proteinSweep
            if (fatSweep > gap)     canvas.drawArc(oval, start + gap / 2f, fatSweep     - gap, false, fatPaint)
            start += fatSweep
            if (carbSweep > gap)    canvas.drawArc(oval, start + gap / 2f, carbSweep    - gap, false, carbPaint)
        }

        val calText  = totalCalories.toString()
        val label    = if (targetCalories > 0) "/ $targetCalories kcal" else "kcal"
        val calH     = calPaint.descent()   - calPaint.ascent()
        val labelH   = labelPaint.descent() - labelPaint.ascent()
        val gap2     = dp(4f)
        val groupH   = calH + gap2 + labelH
        val topY     = cy - groupH / 2f

        canvas.drawText(calText, cx, topY + calH              - calPaint.descent(),   calPaint)
        canvas.drawText(label,   cx, topY + calH + gap2 + labelH - labelPaint.descent(), labelPaint)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity
}
