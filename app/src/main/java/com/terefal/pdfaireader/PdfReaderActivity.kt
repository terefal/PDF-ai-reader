package com.terefal.pdfaireader

import androidx.appcompat.app.AppCompatActivity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import com.github.barteksc.pdfviewer.PDFView
import com.terefal.pdfaireader.config.SettingsManager
import com.terefal.pdfaireader.pdf.PdfTextExtractor
import com.terefal.pdfaireader.viewmodel.PdfReaderViewModel

class PdfReaderActivity : AppCompatActivity() {

    private lateinit var viewModel: PdfReaderViewModel
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var chatContainer: LinearLayout
    private lateinit var questionInput: EditText
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_reader)

        val pdfView: PDFView = findViewById(R.id.pdfView)
        val pdfUri: Uri? = intent.getStringExtra("PDF_URI")?.let { Uri.parse(it) }

        // Initialize PDFBox
        PdfTextExtractor.init(applicationContext)

        if (pdfUri != null) {
            pdfView.fromUri(pdfUri)
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .load()
        } else {
            Toast.makeText(this, "Unable to load PDF", Toast.LENGTH_SHORT).show()
        }

        // Setup ViewModel
        viewModel = ViewModelProvider(this)[PdfReaderViewModel::class.java]
        val settings = SettingsManager(this)
        viewModel.initProvider(settings)

        // Load PDF text for AI context
        pdfUri?.let { viewModel.loadPdf(contentResolver, it) }

        // Setup AI sidebar
        drawerLayout = findViewById(R.id.drawerLayout)
        chatContainer = findViewById(R.id.chatContainer)
        questionInput = findViewById(R.id.questionInput)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        scrollView = findViewById(R.id.chatScrollView)

        findViewById<Button>(R.id.toggleAiButton).setOnClickListener {
            if (drawerLayout.isDrawerOpen(Gravity.END)) {
                drawerLayout.closeDrawer(Gravity.END)
            } else {
                drawerLayout.openDrawer(Gravity.END)
            }
        }

        findViewById<Button>(R.id.sendButton).setOnClickListener {
            val question = questionInput.text.toString().trim()
            if (question.isNotEmpty()) {
                addChatBubble("你", question)
                questionInput.text.clear()
                viewModel.queryAi(question)
            }
        }

        // Observe AI responses
        viewModel.aiResponse.observe(this) { response ->
            addAiBubble(response)
        }

        viewModel.isLoading.observe(this) { loading ->
            loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
            findViewById<Button>(R.id.sendButton).isEnabled = !loading
        }

        // Observe PDF text extraction status
        viewModel.pdfContext.observe(this) { context ->
            if (context.isNotEmpty()) {
                Toast.makeText(this, "PDF 文本已提取 (${context.length} 字符)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addChatBubble(sender: String, message: String) {
        val bubble = TextView(this).apply {
            text = if (sender == "AI") message else "🧑 $message"
            textSize = 14f
            setPadding(12, 8, 12, 8)
            setTextColor(if (sender == "AI") Color.BLACK else Color.WHITE)
            setBackgroundColor(if (sender == "AI") Color.parseColor("#E0E0E0") else Color.parseColor("#6200EE"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8)
            }
        }
        chatContainer.addView(bubble)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addAiBubble(message: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8)
            }
        }

        val bubble = TextView(this).apply {
            text = message
            textSize = 14f
            setPadding(12, 8, 12, 8)
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(bubble)

        // Save note button
        val saveButton = Button(this).apply {
            text = "保存为笔记"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 4, 0, 0)
            }
            setOnClickListener {
                viewModel.saveLastResponseAsNote()
                Toast.makeText(this@PdfReaderActivity, "已保存到笔记", Toast.LENGTH_SHORT).show()
            }
        }
        container.addView(saveButton)

        chatContainer.addView(container)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }
}
