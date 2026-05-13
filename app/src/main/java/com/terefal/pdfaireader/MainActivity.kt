package com.terefal.pdfaireader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.terefal.pdfaireader.ai.AiProvider
import com.terefal.pdfaireader.ai.OpenAiProvider

class MainActivity : Activity() {

    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private var selectedFileUri: Uri? = null
    private lateinit var selectedAiProvider: AiProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize AI provider (default to OpenAI)
        selectedAiProvider = OpenAiProvider()

        // Button to choose PDF
        val openPdfButton: Button = findViewById(R.id.openPdfButton)
        openPdfButton.setOnClickListener {
            openFilePicker()
        }

        // Setup file picker launcher
        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                selectedFileUri = result.data?.data
                selectedFileUri?.let {
                    val intent = Intent(this, PdfReaderActivity::class.java).apply {
                        putExtra("PDF_URI", it.toString())
                    }
                    startActivity(intent)
                } ?: Toast.makeText(this, "No PDF selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/pdf"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerLauncher.launch(intent)
    }
}