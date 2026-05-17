package com.example.macromax

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class ScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dp = context.resources.displayMetrics.density

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xBB000000.toInt()
        style = Paint.Style.FILL
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = 0xFFFFFFFF.toInt()
        strokeWidth = 3f * dp
        style       = Paint.Style.STROKE
        strokeCap   = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val w    = width.toFloat()
        val h    = height.toFloat()
        val size = minOf(w, h) * 0.68f
        val cx   = w / 2f
        val cy   = h * 0.44f   // slightly above center
        val left  = cx - size / 2f
        val top   = cy - size / 2f
        val right = cx + size / 2f
        val bot   = cy + size / 2f

        // Dim overlay with transparent window cutout
        val path = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, w, h, Path.Direction.CW)
            addRoundRect(RectF(left, top, right, bot), 12f * dp, 12f * dp, Path.Direction.CW)
        }
        canvas.drawPath(path, dimPaint)

        // Corner brackets
        val len = size * 0.11f
        val r   = 12f * dp

        // Top-left
        canvas.drawLine(left + r, top,       left + r + len, top,       cornerPaint)
        canvas.drawLine(left,     top + r,   left,           top + r + len, cornerPaint)
        // Top-right
        canvas.drawLine(right - r - len, top,       right - r, top,       cornerPaint)
        canvas.drawLine(right,           top + r,   right,     top + r + len, cornerPaint)
        // Bottom-left
        canvas.drawLine(left + r, bot,       left + r + len, bot,       cornerPaint)
        canvas.drawLine(left,     bot - r,   left,           bot - r - len, cornerPaint)
        // Bottom-right
        canvas.drawLine(right - r - len, bot,       right - r, bot,       cornerPaint)
        canvas.drawLine(right,           bot - r,   right,     bot - r - len, cornerPaint)
    }
}
