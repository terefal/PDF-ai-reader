package com.terefal.pdfaireader.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.github.barteksc.pdfviewer.PDFView

class SelectionOverlay(context: Context, private val pdfView: PDFView) : View(context) {

    private val paint = Paint().apply {
        color = Color.parseColor("#4040A0FF")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#FF4080FF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isSelecting = false

    var isSelectionEnabled = false
    var onSelectionComplete: ((android.graphics.Rect) -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isSelectionEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                endX = startX
                endY = startY
                isSelecting = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                endX = event.x
                endY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                isSelecting = false
                invalidate()
                val rect = android.graphics.Rect(
                    minOf(startX, endX).toInt(),
                    minOf(startY, endY).toInt(),
                    maxOf(startX, endX).toInt(),
                    maxOf(startY, endY).toInt()
                )
                if (rect.width() > 20 && rect.height() > 20) {
                    onSelectionComplete?.invoke(rect)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isSelecting) return

        val left = minOf(startX, endX)
        val top = minOf(startY, endY)
        val right = maxOf(startX, endX)
        val bottom = maxOf(startY, endY)

        val rect = RectF(left, top, right, bottom)
        canvas.drawRect(rect, paint)
        canvas.drawRect(rect, borderPaint)
    }
}
