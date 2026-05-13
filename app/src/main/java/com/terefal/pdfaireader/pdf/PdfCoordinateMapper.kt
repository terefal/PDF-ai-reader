package com.terefal.pdfaireader.pdf

import android.graphics.RectF
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.util.FitPolicy

object PdfCoordinateMapper {

    data class PdfPosition(
        val pageIndex: Int,
        val pageRect: RectF  // in PDF points (1pt = 1/72 inch)
    )

    fun screenToPdf(pdfView: PDFView, screenRect: android.graphics.Rect): PdfPosition {
        val pageIndex = pdfView.currentPage

        // Get page dimensions in PDF points
        val pageSize = pdfView.getPageSize(pageIndex)
        val pageW = pageSize.width.toFloat()
        val pageH = pageSize.height.toFloat()

        // Calculate the rendered page bounds on screen
        val config = pdfView.pageFitPolicy
        val viewW = pdfView.width.toFloat()
        val viewH = pdfView.height.toFloat()

        // Default: FitPolicy.WIDTH
        val scale = viewW / pageW
        val renderedH = pageH * scale

        val pageLeft = 0f
        val pageTop = (viewH - renderedH) / 2f - pdfView.currentYOffset

        // Convert screen coords to PDF points
        val pdfLeft = (screenRect.left - pageLeft) / scale
        val pdfTop = (screenRect.top - pageTop) / scale
        val pdfRight = (screenRect.right - pageLeft) / scale
        val pdfBottom = (screenRect.bottom - pageTop) / scale

        // Clamp to page bounds
        val rect = RectF(
            pdfLeft.coerceIn(0f, pageW),
            pdfTop.coerceIn(0f, pageH),
            pdfRight.coerceIn(0f, pageW),
            pdfBottom.coerceIn(0f, pageH)
        )

        return PdfPosition(pageIndex, rect)
    }
}
