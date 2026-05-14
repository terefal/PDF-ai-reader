package com.terefal.pdfaireader.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.github.barteksc.pdfviewer.PDFView
import com.terefal.pdfaireader.data.Annotation
import com.terefal.pdfaireader.data.AnnotationType

class AnnotationOverlay(context: Context, private val pdfView: PDFView) : View(context) {

    private val annotations: MutableList<Annotation> = mutableListOf()
    private var currentPage: Int = 0

    private val highlightPaint = Paint().apply {
        style = Paint.Style.FILL
        alpha = 100
    }
    private val underlinePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val noteIndicatorPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFFF9800")
    }

    fun setAnnotations(list: List<Annotation>, page: Int) {
        annotations.clear()
        annotations.addAll(list)
        this.currentPage = page
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (annotations.isEmpty()) return

        val pageSize = pdfView.getPageSize(currentPage)
        val pageW = pageSize.width.toFloat()
        val pageH = pageSize.height.toFloat()

        val viewW = pdfView.width.toFloat()
        val viewH = pdfView.height.toFloat()
        val scale = viewW / pageW
        val renderedH = pageH * scale
        val pageTop = (viewH - renderedH) / 2f - pdfView.currentYOffset

        for (ann in annotations) {
            if (ann.pageNumber != currentPage) continue

            val left = ann.rectLeft * scale
            val top = ann.rectTop * scale + pageTop
            val right = ann.rectRight * scale
            val bottom = ann.rectBottom * scale + pageTop

            val rect = RectF(left, top, right, bottom)

            when (ann.type) {
                AnnotationType.HIGHLIGHT -> {
                    highlightPaint.color = ann.color
                    canvas.drawRect(rect, highlightPaint)
                }
                AnnotationType.UNDERLINE -> {
                    underlinePaint.color = ann.color
                    val y = bottom - 2f
                    canvas.drawLine(rect.left, y, rect.right, y, underlinePaint)
                }
                AnnotationType.NOTE -> {
                    highlightPaint.color = 0x40FFEB3B.toInt()
                    canvas.drawRect(rect, highlightPaint)
                    canvas.drawCircle(rect.left + 8f, rect.top + 8f, 8f, noteIndicatorPaint)
                }
            }
        }
    }
}
