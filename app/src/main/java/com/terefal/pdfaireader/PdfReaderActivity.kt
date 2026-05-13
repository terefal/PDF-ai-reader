package com.terefal.pdfaireader

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import com.github.barteksc.pdfviewer.PDFView

class PdfReaderActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_reader)

        val pdfView: PDFView = findViewById(R.id.pdfView)

        // Load a placeholder PDF (Update the file path as needed)
        val pdfUri: Uri = Uri.parse("file:///android_asset/sample.pdf")
        pdfView.fromUri(pdfUri)
            .enableSwipe(true) // enables vertical scroll
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .load()
    }
}