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
import com.terefal.pdfaireader.ai.ChatImage
import com.terefal.pdfaireader.config.SettingsManager
import com.terefal.pdfaireader.data.Annotation
import com.terefal.pdfaireader.data.AnnotationType
import com.terefal.pdfaireader.data.AppDatabase
import com.terefal.pdfaireader.pdf.AnnotationOverlay
import com.terefal.pdfaireader.pdf.PdfCoordinateMapper
import com.terefal.pdfaireader.pdf.PdfTextExtractor
import com.terefal.pdfaireader.pdf.SelectionOverlay
import com.terefal.pdfaireader.viewmodel.PdfReaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class PdfReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PDF_URI = "PDF_URI"
        private const val MAX_CHAT_ITEMS = 50
    }

    private lateinit var viewModel: PdfReaderViewModel
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var chatContainer: LinearLayout
    private lateinit var questionInput: EditText
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var scrollView: ScrollView
    private lateinit var webSearchSwitch: Switch
    private lateinit var pdfView: PDFView
    private lateinit var sendButton: Button
    private lateinit var circleSelectButton: Button
    private lateinit var annotateButton: Button

    private var currentImages: MutableList<ChatImage> = mutableListOf()
    private var currentThumbnails: MutableList<Bitmap> = mutableListOf()
    private var pdfUri: Uri? = null

    private lateinit var selectionOverlay: SelectionOverlay
    private var isCircleSelectMode = false

    private lateinit var annotationOverlay: AnnotationOverlay
    private var isAnnotationMode = false
    private var annotationCollectJob: Job? = null
    private val annotationDao by lazy { AppDatabase.getInstance(this).annotationDao() }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processSelectedImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_reader)

        pdfView = findViewById(R.id.pdfView)
        pdfUri = intent.getStringExtra(EXTRA_PDF_URI)?.let { Uri.parse(it) }

        PdfTextExtractor.init(applicationContext)

        val uri = pdfUri
        if (uri != null) {
            pdfView.fromUri(uri)
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .load()
        } else {
            Toast.makeText(this, "Unable to load PDF", Toast.LENGTH_SHORT).show()
        }

        val overlayContainer = findViewById<FrameLayout>(R.id.overlayContainer)

        selectionOverlay = SelectionOverlay(this, pdfView).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            isSelectionEnabled = false
            onSelectionComplete = { rect -> handleSelectionComplete(rect) }
        }
        overlayContainer.addView(selectionOverlay)

        annotationOverlay = AnnotationOverlay(this, pdfView).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        overlayContainer.addView(annotationOverlay)

        viewModel = ViewModelProvider(this)[PdfReaderViewModel::class.java]
        val settings = SettingsManager(this)
        viewModel.initProvider(settings)

        uri?.let { viewModel.loadPdf(contentResolver, it) }

        drawerLayout = findViewById(R.id.drawerLayout)
        chatContainer = findViewById(R.id.chatContainer)
        questionInput = findViewById(R.id.questionInput)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        scrollView = findViewById(R.id.chatScrollView)
        webSearchSwitch = findViewById(R.id.webSearchSwitch)
        sendButton = findViewById(R.id.sendButton)
        circleSelectButton = findViewById(R.id.circleSelectButton)
        annotateButton = findViewById(R.id.annotateButton)

        findViewById<Button>(R.id.toggleAiButton).setOnClickListener {
            if (drawerLayout.isDrawerOpen(Gravity.END)) drawerLayout.closeDrawer(Gravity.END)
            else drawerLayout.openDrawer(Gravity.END)
        }

        findViewById<Button>(R.id.imageButton).setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        sendButton.setOnClickListener {
            val question = questionInput.text.toString().trim()
            if (question.isNotEmpty() || currentImages.isNotEmpty()) {
                if (currentImages.isNotEmpty()) addImageBubble(currentThumbnails, currentImages.size)
                if (question.isNotEmpty()) addUserBubble(question)
                questionInput.text.clear()
                val images = currentImages.toList()
                currentImages.clear()
                currentThumbnails.clear()
                viewModel.queryAi(question, images, webSearchSwitch.isChecked)
            }
        }

        circleSelectButton.setOnClickListener {
            setToolMode(circleSelect = !isCircleSelectMode, annotate = false)
        }

        annotateButton.setOnClickListener {
            setToolMode(circleSelect = false, annotate = !isAnnotationMode)
        }

        viewModel.aiResponse.observe(this) { addAiBubble(it) }

        viewModel.isLoading.observe(this) { loading ->
            loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
            sendButton.isEnabled = !loading
        }

        viewModel.pdfContext.observe(this) { context ->
            if (context.isNotEmpty()) {
                Toast.makeText(this, "PDF 文本已提取 (${context.length} 字符)", Toast.LENGTH_SHORT).show()
            }
        }

        loadAnnotationsForPage(pdfView.currentPage)
    }

    private fun setToolMode(circleSelect: Boolean, annotate: Boolean) {
        isCircleSelectMode = circleSelect
        isAnnotationMode = annotate
        selectionOverlay.isSelectionEnabled = circleSelect || annotate
        val active = Color.parseColor("#FF4080FF")
        val inactive = Color.parseColor("#FFD6D6D6")
        circleSelectButton.setBackgroundColor(if (circleSelect) active else inactive)
        annotateButton.setBackgroundColor(if (annotate) active else inactive)
        val msg = when {
            circleSelect -> "圈画模式：在 PDF 上拖动选择区域"
            annotate -> "标注模式：选择区域后添加标注"
            else -> "阅读模式"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private suspend fun mapScreenToPdf(screenRect: Rect): com.terefal.pdfaireader.pdf.PdfCoordinateMapper.PdfPosition =
        withContext(Dispatchers.IO) { PdfCoordinateMapper.screenToPdf(pdfView, screenRect) }

    private fun handleSelectionComplete(screenRect: Rect) {
        if (isCircleSelectMode) {
            lifecycleScope.launch {
                try {
                    val position = mapScreenToPdf(screenRect)
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
            showAnnotationPopup(screenRect)
        }
    }

    private fun showAnnotationPopup(screenRect: Rect) {
        val options = arrayOf("🟡 高亮", "➖ 下划线", "📝 批注")
        AlertDialog.Builder(this)
            .setTitle("标注类型")
            .setItems(options) { _, which ->
                val type = when (which) {
                    0 -> AnnotationType.HIGHLIGHT
                    1 -> AnnotationType.UNDERLINE
                    2 -> AnnotationType.NOTE
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
                        val position = mapScreenToPdf(screenRect)
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
                        if (type == AnnotationType.NOTE) showNoteEditDialog(id)
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
        val input = EditText(this).apply { hint = "输入批注文字..."; minLines = 3 }
        AlertDialog.Builder(this)
            .setTitle("批注内容")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        annotationDao.updateText(annotationId, input.text.toString().trim())
                    }
                    loadAnnotationsForPage(pdfView.currentPage)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadAnnotationsForPage(page: Int) {
        annotationCollectJob?.cancel()
        annotationCollectJob = lifecycleScope.launch {
            annotationDao.getAnnotationsForPage(pdfUri?.toString() ?: "", page).collect { list ->
                annotationOverlay.setAnnotations(list, page)
            }
        }
    }

    private fun trimChatItems() {
        while (chatContainer.childCount > MAX_CHAT_ITEMS) {
            chatContainer.removeViewAt(0)
        }
    }

    // --- Image handling ---

    private fun processSelectedImage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap == null) { Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show(); return }
                val maxDim = 1024
                val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val s = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt(), (bitmap.height * s).toInt(), true)
                } else bitmap

                val os = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 60, os)
                val b64 = android.util.Base64.encodeToString(os.toByteArray(), android.util.Base64.NO_WRAP)
                currentImages.add(ChatImage(base64 = b64, mimeType = "image/jpeg"))
                currentThumbnails.add(scaled)
                addImageBubble(currentThumbnails, currentImages.size)
                Toast.makeText(this, "图片已添加 (${currentImages.size} 张)", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "图片处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Chat bubbles ---

    private fun addImageBubble(thumbnails: List<Bitmap>, count: Int) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
        }
        thumbnails.lastOrNull()?.let { bitmap ->
            ImageView(this).apply {
                setImageBitmap(bitmap)
                layoutParams = LinearLayout.LayoutParams(200, 200).apply { setMargins(0, 0, 8, 0) }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }.also { container.addView(it) }
        }
        TextView(this).apply {
            text = "📷 $count 张图片"
            textSize = 12f; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#6200EE"))
            setPadding(8, 4, 8, 4)
        }.also { container.addView(it) }
        chatContainer.addView(container)
        trimChatItems()
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addUserBubble(message: String) {
        TextView(this).apply {
            text = "🧑 $message"; textSize = 14f
            setPadding(12, 8, 12, 8); setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#6200EE"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
        }.also { chatContainer.addView(it) }
        trimChatItems()
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addAiBubble(message: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
        }
        TextView(this).apply {
            text = message; textSize = 14f
            setPadding(12, 8, 12, 8); setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }.also { container.addView(it) }
        Button(this).apply {
            text = "保存为笔记"; textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 4, 0, 0) }
            setOnClickListener {
                viewModel.saveLastResponseAsNote()
                Toast.makeText(this@PdfReaderActivity, "已保存到笔记", Toast.LENGTH_SHORT).show()
            }
        }.also { container.addView(it) }
        chatContainer.addView(container)
        trimChatItems()
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }
}
