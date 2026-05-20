package com.example.macromax

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.sin

class ActivityRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 0.0 – 1.0 fill of the ring */
    var progress: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    private val ringColor  = Color.parseColor("#F44336")
    private val trackColor = ColorUtils.setAlphaComponent(ringColor, 55)  // ~22% opacity

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = dp(22f)
        color       = trackColor
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = dp(22f)
        color       = ringColor
        strokeCap   = Paint.Cap.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ringColor
        style = Paint.Style.FILL
    }

    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        val cx     = width  / 2f
        val cy     = height / 2f
        val sw     = dp(22f)
        val radius = minOf(cx, cy) - sw / 2f - dp(3f)

        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // Track — full circle background
        trackPaint.strokeWidth = sw
        canvas.drawCircle(cx, cy, radius, trackPaint)

        // Progress arc — starts at 12 o'clock (-90°)
        if (progress > 0f) {
            arcPaint.strokeWidth = sw
            canvas.drawArc(oval, -90f, progress * 360f, false, arcPaint)

            // Glowing dot at arc head
            val angleRad = Math.toRadians((-90.0 + progress * 360.0))
            val dotX = (cx + radius * cos(angleRad)).toFloat()
            val dotY = (cy + radius * sin(angleRad)).toFloat()
            canvas.drawCircle(dotX, dotY, sw / 2f + dp(1f), dotPaint)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
