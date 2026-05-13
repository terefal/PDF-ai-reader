package com.terefal.pdfaireader

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.github.barteksc.pdfviewer.PDFView

class PdfReaderActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_reader)

        val pdfView: PDFView = findViewById(R.id.pdfView)
        val pdfUri: Uri? = intent.getStringExtra("PDF_URI")?.let { Uri.parse(it) }

        if (pdfUri != null) {
            pdfView.fromUri(pdfUri)
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .load()
        } else {
            Toast.makeText(this, "Unable to load PDF", Toast.LENGTH_SHORT).show()
        }
    }
}