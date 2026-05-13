package com.terefal.pdfaireader.pdf

import android.content.ContentResolver
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
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
}
