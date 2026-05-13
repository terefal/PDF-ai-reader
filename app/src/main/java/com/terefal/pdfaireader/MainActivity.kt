package com.terefal.pdfaireader

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import com.github.barteksc.pdfviewer.PDFView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val openPdfButton: Button = findViewById(R.id.openPdfButton)
        openPdfButton.setOnClickListener {
            val intent = Intent(this, PdfReaderActivity::class.java)
            startActivity(intent)
        }
    }
}