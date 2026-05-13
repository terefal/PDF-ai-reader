package com.terefal.pdfaireader

import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.terefal.pdfaireader.ai.SettingsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private var selectedFileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Button to choose PDF
        findViewById<Button>(R.id.openPdfButton).setOnClickListener {
            openFilePicker()
        }

        // Button to open settings
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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
