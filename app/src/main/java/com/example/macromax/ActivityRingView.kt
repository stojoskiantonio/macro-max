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
        strokeCap   = Paint.Cap.BUTT
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val oval      = RectF()
    private val arrowPath = Path()

    override fun onDraw(canvas: Canvas) {
        val cx     = width  / 2f
        val cy     = height / 2f
        val sw     = dp(22f)
        val radius = minOf(cx, cy) - sw / 2f - dp(3f)

        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // Track — full circle background
        trackPaint.strokeWidth = sw
        canvas.drawCircle(cx, cy, radius, trackPaint)

        // Progress arc — starts at 12 o'clock (-90°), BUTT cap so arrow sits cleanly
        if (progress > 0f) {
            arcPaint.strokeWidth = sw
            canvas.drawArc(oval, -90f, progress * 360f, false, arcPaint)

            // ── Arrow pointer at the arc head ──────────────────────────────
            val angleDeg = -90f + progress * 360f
            val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()

            // Outward radial unit vector
            val nx = cos(angleRad)
            val ny = sin(angleRad)
            // Perpendicular (tangential) unit vector
            val px = -sin(angleRad)
            val py =  cos(angleRad)

            // Tip: just outside the ring's outer edge
            val tipX = cx + (radius + sw / 2f + dp(5f)) * nx
            val tipY = cy + (radius + sw / 2f + dp(5f)) * ny

            // Base centre: just inside the ring's inner edge
            val baseCx = cx + (radius - sw / 2f - dp(2f)) * nx
            val baseCy = cy + (radius - sw / 2f - dp(2f)) * ny

            val hw = dp(6f)   // half-width of arrow base
            arrowPath.reset()
            arrowPath.moveTo(tipX, tipY)
            arrowPath.lineTo(baseCx + hw * px, baseCy + hw * py)
            arrowPath.lineTo(baseCx - hw * px, baseCy - hw * py)
            arrowPath.close()
            canvas.drawPath(arrowPath, arrowPaint)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
