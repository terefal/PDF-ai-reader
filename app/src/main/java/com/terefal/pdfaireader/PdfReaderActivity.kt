package com.terefal.pdfaireader

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.terefal.pdfaireader.ai.ChatImage
import com.terefal.pdfaireader.config.SettingsManager
import com.terefal.pdfaireader.data.Annotation
import com.terefal.pdfaireader.data.AppDatabase
import com.terefal.pdfaireader.pdf.AnnotationOverlay
import com.terefal.pdfaireader.pdf.PdfCoordinateMapper
import com.terefal.pdfaireader.pdf.PdfTextExtractor
import com.terefal.pdfaireader.pdf.SelectionOverlay
import com.terefal.pdfaireader.viewmodel.PdfReaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class PdfReaderActivity : AppCompatActivity() {

    private lateinit var viewModel: PdfReaderViewModel
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var chatContainer: LinearLayout
    private lateinit var questionInput: EditText
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var scrollView: ScrollView
    private lateinit var webSearchSwitch: Switch
    private lateinit var pdfView: PDFView

    private var currentImages: MutableList<ChatImage> = mutableListOf()
    private var pdfUri: Uri? = null

    // Circle select
    private lateinit var selectionOverlay: SelectionOverlay
    private var isCircleSelectMode = false

    // Annotation
    private lateinit var annotationOverlay: AnnotationOverlay
    private var isAnnotationMode = false
    private val annotationDao by lazy { AppDatabase.getInstance(this).annotationDao() }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processSelectedImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_reader)

        pdfView = findViewById(R.id.pdfView)
        pdfUri = intent.getStringExtra("PDF_URI")?.let { Uri.parse(it) }

        PdfTextExtractor.init(applicationContext)

        if (pdfUri != null) {
            pdfView.fromUri(pdfUri!!)
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .load()
        } else {
            Toast.makeText(this, "Unable to load PDF", Toast.LENGTH_SHORT).show()
        }

        // Setup overlays
        val overlayContainer = findViewById<FrameLayout>(R.id.overlayContainer)

        selectionOverlay = SelectionOverlay(this, pdfView).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isEnabled = false
            onSelectionComplete = { rect -> handleSelectionComplete(rect) }
        }
        overlayContainer.addView(selectionOverlay)

        annotationOverlay = AnnotationOverlay(this, pdfView).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        overlayContainer.addView(annotationOverlay)

        // Page change listener for annotation overlay
        pdfView.setOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageChanged(page: Int, pageCount: Int) {
                loadAnnotationsForPage(page)
            }
        })

        // Setup ViewModel
        viewModel = ViewModelProvider(this)[PdfReaderViewModel::class.java]
        val settings = SettingsManager(this)
        viewModel.initProvider(settings)

        pdfUri?.let { viewModel.loadPdf(contentResolver, it) }

        // Setup AI sidebar
        drawerLayout = findViewById(R.id.drawerLayout)
        chatContainer = findViewById(R.id.chatContainer)
        questionInput = findViewById(R.id.questionInput)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        scrollView = findViewById(R.id.chatScrollView)
        webSearchSwitch = findViewById(R.id.webSearchSwitch)

        // AI sidebar toggle
        findViewById<Button>(R.id.toggleAiButton).setOnClickListener {
            if (drawerLayout.isDrawerOpen(Gravity.END)) {
                drawerLayout.closeDrawer(Gravity.END)
            } else {
                drawerLayout.openDrawer(Gravity.END)
            }
        }

        // Image picker button
        findViewById<Button>(R.id.imageButton).setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // Send button
        findViewById<Button>(R.id.sendButton).setOnClickListener {
            val question = questionInput.text.toString().trim()
            if (question.isNotEmpty() || currentImages.isNotEmpty()) {
                if (currentImages.isNotEmpty()) {
                    addImageBubble(currentImages.toList())
                }
                if (question.isNotEmpty()) {
                    addUserBubble(question)
                }
                questionInput.text.clear()
                val images = currentImages.toList()
                currentImages.clear()
                viewModel.queryAi(question, images, webSearchSwitch.isChecked)
            }
        }

        // Circle select mode toggle
        findViewById<Button>(R.id.circleSelectButton).setOnClickListener {
            isCircleSelectMode = !isCircleSelectMode
            isAnnotationMode = false
            selectionOverlay.isEnabled = isCircleSelectMode
            findViewById<Button>(R.id.circleSelectButton).apply {
                setBackgroundColor(if (isCircleSelectMode) Color.parseColor("#FF4080FF") else Color.parseColor("#FFD6D6D6"))
            }
            findViewById<Button>(R.id.annotateButton).apply {
                setBackgroundColor(Color.parseColor("#FFD6D6D6"))
            }
            Toast.makeText(this, if (isCircleSelectMode) "圈画模式：在 PDF 上拖动选择区域" else "圈画模式已关闭", Toast.LENGTH_SHORT).show()
        }

        // Annotation mode toggle
        findViewById<Button>(R.id.annotateButton).setOnClickListener {
            isAnnotationMode = !isAnnotationMode
            isCircleSelectMode = false
            selectionOverlay.isEnabled = isAnnotationMode  // share overlay for annotation selection too
            findViewById<Button>(R.id.annotateButton).apply {
                setBackgroundColor(if (isAnnotationMode) Color.parseColor("#FF4080FF") else Color.parseColor("#FFD6D6D6"))
            }
            findViewById<Button>(R.id.circleSelectButton).apply {
                setBackgroundColor(Color.parseColor("#FFD6D6D6"))
            }
            Toast.makeText(this, if (isAnnotationMode) "标注模式：选择区域后添加标注" else "标注模式已关闭", Toast.LENGTH_SHORT).show()
        }

        // Observe AI responses
        viewModel.aiResponse.observe(this) { response ->
            addAiBubble(response)
        }

        viewModel.isLoading.observe(this) { loading ->
            loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
            findViewById<Button>(R.id.sendButton).isEnabled = !loading
        }

        viewModel.pdfContext.observe(this) { context ->
            if (context.isNotEmpty()) {
                Toast.makeText(this, "PDF 文本已提取 (${context.length} 字符)", Toast.LENGTH_SHORT).show()
            }
        }

        // Load initial annotations
        pdfView.currentPage.let { loadAnnotationsForPage(it) }
    }

    private fun handleSelectionComplete(screenRect: Rect) {
        if (isCircleSelectMode) {
            // Circle select → extract text + query AI
            lifecycleScope.launch {
                try {
                    val position = withContext(Dispatchers.IO) {
                        PdfCoordinateMapper.screenToPdf(pdfView, screenRect)
                    }
                    val text = withContext(Dispatchers.IO) {
                        PdfTextExtractor.extractTextByArea(contentResolver, pdfUri!!, position.pageIndex + 1, position.pageRect)
                    }
                    drawerLayout.openDrawer(Gravity.END)
                    addUserBubble("圈选区域 (第${position.pageIndex + 1}页):\n$text")
                    viewModel.queryAi("请分析以下选中的文档内容:\n$text")
                } catch (e: Exception) {
                    Toast.makeText(this@PdfReaderActivity, "提取失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (isAnnotationMode) {
            // Annotation mode → show popup
            showAnnotationPopup(screenRect)
        }
    }

    private fun showAnnotationPopup(screenRect: Rect) {
        val options = arrayOf("🟡 高亮", "➖ 下划线", "📝 批注")
        AlertDialog.Builder(this)
            .setTitle("标注类型")
            .setItems(options) { _, which ->
                val type = when (which) {
                    0 -> "highlight"
                    1 -> "underline"
                    2 -> "note"
                    else -> return@setItems
                }
                val color = when (which) {
                    0 -> 0x60FFEB3B.toInt()
                    1 -> 0xFFF44336.toInt()
                    2 -> 0x80FF9800.toInt()
                    else -> 0
                }

                lifecycleScope.launch {
                    try {
                        val position = withContext(Dispatchers.IO) {
                            PdfCoordinateMapper.screenToPdf(pdfView, screenRect)
                        }
                        val ann = Annotation(
                            pdfUri = pdfUri?.toString() ?: "",
                            pageNumber = position.pageIndex,
                            rectLeft = position.pageRect.left,
                            rectTop = position.pageRect.top,
                            rectRight = position.pageRect.right,
                            rectBottom = position.pageRect.bottom,
                            color = color,
                            type = type
                        )
                        val id = withContext(Dispatchers.IO) { annotationDao.insert(ann) }

                        if (type == "note") {
                            showNoteEditDialog(id)
                        }

                        loadAnnotationsForPage(position.pageIndex)
                        Toast.makeText(this@PdfReaderActivity, "标注已保存", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@PdfReaderActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showNoteEditDialog(annotationId: Long) {
        val input = EditText(this).apply {
            hint = "输入批注文字..."
            minLines = 3
        }
        AlertDialog.Builder(this)
            .setTitle("批注内容")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                lifecycleScope.launch {
                    val ann = withContext(Dispatchers.IO) { annotationDao.getAnnotationsForPdf(pdfUri?.toString() ?: "") }
                    // Update the note text via Room
                    withContext(Dispatchers.IO) {
                        // Find by ID and update
                        val db = AppDatabase.getInstance(this@PdfReaderActivity)
                        db.openHelper.writableDatabase.execSQL(
                            "UPDATE annotations SET text = ? WHERE id = ?",
                            arrayOf(input.text.toString().trim(), annotationId)
                        )
                    }
                    loadAnnotationsForPage(pdfView.currentPage)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadAnnotationsForPage(page: Int) {
        lifecycleScope.launch {
            annotationDao.getAnnotationsForPage(pdfUri?.toString() ?: "", page).collect { list ->
                annotationOverlay.setAnnotations(list, page)
            }
        }
    }

    // --- Image handling ---

    private fun processSelectedImage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap == null) {
                    Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show()
                    return
                }
                val maxDim = 1024
                val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                } else bitmap

                val outputStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
                val base64 = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)

                currentImages.add(ChatImage(base64 = base64, mimeType = "image/jpeg"))
                addImageBubble(currentImages.toList())
                Toast.makeText(this, "图片已添加 (${currentImages.size} 张)", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "图片处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Chat bubbles ---

    private fun addImageBubble(images: List<ChatImage>) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
        }
        val lastImg = images.last()
        val bytes = android.util.Base64.decode(lastImg.base64, android.util.Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val imageView = ImageView(this).apply {
            setImageBitmap(bitmap)
            layoutParams = LinearLayout.LayoutParams(200, 200).apply { setMargins(0, 0, 8, 0) }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        container.addView(imageView)
        val textView = TextView(this).apply {
            text = "📷 ${images.size} 张图片"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#6200EE"))
            setPadding(8, 4, 8, 4)
        }
        container.addView(textView)
        chatContainer.addView(container)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addUserBubble(message: String) {
        val bubble = TextView(this).apply {
            text = "🧑 $message"
            textSize = 14f
            setPadding(12, 8, 12, 8)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#6200EE"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
        }
        chatContainer.addView(bubble)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addAiBubble(message: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
        }
        val bubble = TextView(this).apply {
            text = message
            textSize = 14f
            setPadding(12, 8, 12, 8)
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(bubble)
        val saveButton = Button(this).apply {
            text = "保存为笔记"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 4, 0, 0) }
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
