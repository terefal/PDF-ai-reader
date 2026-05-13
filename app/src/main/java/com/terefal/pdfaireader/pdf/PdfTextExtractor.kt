package com.terefal.pdfaireader.pdf

import android.content.ContentResolver
import android.graphics.RectF
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PdfTextExtractor {

    fun init(context: android.content.Context) {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun extractText(contentResolver: ContentResolver, uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    PDFTextStripper().apply {
                        sortByPosition = true
                        startPage = 1
                        endPage = document.numberOfPages
                    }.getText(document)
                }
            } ?: ""
        } catch (e: Exception) {
            "[PDF文本提取失败: ${e.message}]"
        }
    }

    suspend fun extractTextByArea(
        contentResolver: ContentResolver,
        uri: Uri,
        pageIndex: Int,
        area: RectF
    ): String = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    if (pageIndex in 1..document.numberOfPages) {
                        val stripper = PDFTextStripperByArea().apply {
                            sortByPosition = true
                            addRegion("selection", area)
                            startPage = pageIndex
                            endPage = pageIndex
                            extractRegions(document.getPage(pageIndex - 1))
                        }
                        val text = stripper.getTextForRegion("selection")
                        if (text.isNullOrBlank()) {
                            // Fallback: extract full page text
                            PDFTextStripper().apply {
                                sortByPosition = true
                                startPage = pageIndex
                                endPage = pageIndex
                            }.getText(document)
                        } else text
                    } else ""
                }
            } ?: ""
        } catch (e: Exception) {
            "[PDF区域提取失败: ${e.message}]"
        }
    }
}
