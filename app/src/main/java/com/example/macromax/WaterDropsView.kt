package com.example.macromax

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class WaterDropsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var total: Int = 8
        set(v) { field = v.coerceAtLeast(1); invalidate() }
    var filled: Int = 0
        set(v) { field = v.coerceAtLeast(0); invalidate() }

    private val filledColor = Color.parseColor("#64B5F6")
    private val emptyColor  = Color.parseColor("#2064B5F6")  // ~12% opacity

    private val filledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = filledColor }
    private val emptyPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = emptyColor  }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (total <= 0) return

        val w   = width.toFloat()
        val h   = height.toFloat()
        val gap = (3 * resources.displayMetrics.density)
        val slotW = (w - gap * (total - 1)) / total

        for (i in 0 until total) {
            val left = i * (slotW + gap)
            val rect = RectF(left, 0f, left + slotW, h)
            val paint = if (i < filled) filledPaint else emptyPaint
            canvas.drawPath(dropPath(rect), paint)
        }
    }

    private fun dropPath(r: RectF): Path {
        val cx = r.centerX()
        val h  = r.height()
        val hw = r.width() / 2f

        // Circular portion occupies the bottom 65% of the height
        val circleR  = hw * 0.88f
        val circleCy = r.bottom - circleR

        val path = Path()
        path.moveTo(cx, r.top)

        // Right side curve down to circle
        path.cubicTo(
            cx + hw * 0.45f, r.top + h * 0.30f,
            cx + circleR,    circleCy - circleR * 0.55f,
            cx + circleR,    circleCy
        )
        // Bottom arc (right → left)
        path.arcTo(
            RectF(cx - circleR, circleCy - circleR, cx + circleR, circleCy + circleR),
            0f, 180f
        )
        // Left side curve back to top
        path.cubicTo(
            cx - circleR,    circleCy - circleR * 0.55f,
            cx - hw * 0.45f, r.top + h * 0.30f,
            cx,              r.top
        )
        path.close()
        return path
    }
}
