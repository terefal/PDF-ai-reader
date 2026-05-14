package com.terefal.pdfaireader.pdf

import android.graphics.RectF
import com.github.barteksc.pdfviewer.PDFView
object PdfCoordinateMapper {

    data class PdfPosition(
        val pageIndex: Int,
        val pageRect: RectF
    )

    fun screenToPdf(pdfView: PDFView, screenRect: android.graphics.Rect): PdfPosition {
        val pageIndex = pdfView.currentPage
        val pageSize = pdfView.getPageSize(pageIndex)
        val pageW = pageSize.width.toFloat()
        val pageH = pageSize.height.toFloat()

        val viewW = pdfView.width.toFloat()
        val viewH = pdfView.height.toFloat()

        // Width-fit scale (default for android-pdf-viewer)
        val scale = viewW / pageW
        val renderedH = pageH * scale
        val pageTop = (viewH - renderedH) / 2f - pdfView.currentYOffset

        val pdfLeft = screenRect.left / scale
        val pdfTop = (screenRect.top - pageTop) / scale
        val pdfRight = screenRect.right / scale
        val pdfBottom = (screenRect.bottom - pageTop) / scale

        val rect = RectF(
            pdfLeft.coerceIn(0f, pageW),
            pdfTop.coerceIn(0f, pageH),
            pdfRight.coerceIn(0f, pageW),
            pdfBottom.coerceIn(0f, pageH)
        )

        return PdfPosition(pageIndex, rect)
    }
}
