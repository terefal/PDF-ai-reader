package com.terefal.pdfaireader

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import androidx.core.content.ContextCompat
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
        private const val MAX_CHAT_ITEMS = 40
        private val BLUE_500 = 0xFF0969DA.toInt()
        private val GRAY_100 = 0xFFF6F8FA.toInt()
        private val GRAY_200 = 0xFFD0D7DE.toInt()
        private val GRAY_600 = 0xFF656D76.toInt()
        private val GRAY_900 = 0xFF1F2328.toInt()
        private val BLUE_50 = 0xFFDDF4FF.toInt()
        private val WHITE = 0xFFFFFFFF.toInt()
    }

    private lateinit var viewModel: PdfReaderViewModel
    private lateinit var chatContainer: LinearLayout
    private lateinit var questionInput: EditText
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var scrollView: ScrollView
    private lateinit var webSearchSwitch: Switch
    private lateinit var pdfView: PDFView
    private lateinit var sendButton: TextView
    private lateinit var circleChip: TextView
    private lateinit var annotateChip: TextView
    private lateinit var titlePageInfo: TextView

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
        titlePageInfo = findViewById(R.id.titlePageInfo)
        pdfUri = intent.getStringExtra(EXTRA_PDF_URI)?.let { Uri.parse(it) }

        PdfTextExtractor.init(applicationContext)

        val uri = pdfUri
        if (uri != null) {
            pdfView.fromUri(uri).enableSwipe(true).swipeHorizontal(false).enableDoubletap(true).load()
        } else {
            Toast.makeText(this, "Unable to load PDF", Toast.LENGTH_SHORT).show()
        }

        // Overlays for circle/annotation
        val overlayContainer = findViewById<FrameLayout>(R.id.overlayContainer)
        selectionOverlay = SelectionOverlay(this, pdfView).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            isSelectionEnabled = false
            onSelectionComplete = { rect -> handleSelectionComplete(rect) }
        }
        overlayContainer.addView(selectionOverlay)

        annotationOverlay = AnnotationOverlay(this, pdfView).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        overlayContainer.addView(annotationOverlay)

        // ViewModel
        viewModel = ViewModelProvider(this)[PdfReaderViewModel::class.java]
        viewModel.initProvider(SettingsManager(this))
        uri?.let { viewModel.loadPdf(contentResolver, it) }

        // Sidebar
        chatContainer = findViewById(R.id.chatContainer)
        questionInput = findViewById(R.id.questionInput)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        scrollView = findViewById(R.id.chatScrollView)
        webSearchSwitch = findViewById(R.id.webSearchSwitch)
        sendButton = findViewById(R.id.sendButton)
        circleChip = findViewById(R.id.circleChip)
        annotateChip = findViewById(R.id.annotateChip)

        findViewById<TextView>(R.id.imageButton).setOnClickListener { imagePickerLauncher.launch("image/*") }

        sendButton.setOnClickListener { sendMessage() }
        questionInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }

        circleChip.setOnClickListener { setToolMode(circleSelect = !isCircleSelectMode, annotate = false) }
        annotateChip.setOnClickListener { setToolMode(circleSelect = false, annotate = !isAnnotationMode) }

        viewModel.aiResponse.observe(this) { addAiBubble(it) }
        viewModel.isLoading.observe(this) { loading ->
            loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
            sendButton.isEnabled = !loading
            sendButton.alpha = if (loading) 0.5f else 1f
        }
        viewModel.pdfContext.observe(this) { context ->
            if (context.isNotEmpty()) Toast.makeText(this, "PDF 文本已提取 (${context.length} 字符)", Toast.LENGTH_SHORT).show()
        }

        pdfView.setOnClickListener { loadAnnotationsForPage(pdfView.currentPage) }
        loadAnnotationsForPage(pdfView.currentPage)
    }

    private fun sendMessage() {
        val question = questionInput.text.toString().trim()
        if (question.isEmpty() && currentImages.isEmpty()) return
        if (currentImages.isNotEmpty()) addImageBubble(currentThumbnails, currentImages.size)
        if (question.isNotEmpty()) addUserBubble(question)
        questionInput.text.clear()
        val images = currentImages.toList(); currentImages.clear(); currentThumbnails.clear()
        viewModel.queryAi(question, images, webSearchSwitch.isChecked)
    }

    private fun setToolMode(circleSelect: Boolean, annotate: Boolean) {
        isCircleSelectMode = circleSelect; isAnnotationMode = annotate
        selectionOverlay.isSelectionEnabled = circleSelect || annotate

        fun applyChipState(chip: TextView, active: Boolean) {
            chip.background = if (active)
                ContextCompat.getDrawable(this, R.drawable.chip_active)
            else
                ContextCompat.getDrawable(this, R.drawable.chip_inactive)
            chip.setTextColor(if (active) WHITE else GRAY_900)
        }
        applyChipState(circleChip, circleSelect)
        applyChipState(annotateChip, annotate)
    }

    private suspend fun mapScreenToPdf(screenRect: Rect) =
        withContext(Dispatchers.IO) { PdfCoordinateMapper.screenToPdf(pdfView, screenRect) }

    private fun handleSelectionComplete(screenRect: Rect) {
        if (isCircleSelectMode) {
            lifecycleScope.launch {
                try {
                    val position = mapScreenToPdf(screenRect)
                    val text = withContext(Dispatchers.IO) {
                        PdfTextExtractor.extractTextByArea(contentResolver, pdfUri!!, position.pageIndex + 1, position.pageRect)
                    }
                    addUserBubble("已圈选第 ${position.pageIndex + 1} 页区域")
                    questionInput.setText("第${position.pageIndex + 1}页选中内容:\n$text\n\n")
                    questionInput.setSelection(questionInput.text.length)
                    questionInput.requestFocus()
                } catch (e: Exception) {
                    Toast.makeText(this@PdfReaderActivity, "提取失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (isAnnotationMode) {
            showAnnotationPopup(screenRect)
        }
    }

    private fun showAnnotationPopup(screenRect: Rect) {
        AlertDialog.Builder(this)
            .setTitle("标注类型")
            .setItems(arrayOf("🟡 高亮", "➖ 下划线", "📝 批注")) { _, which ->
                val type = when (which) {
                    0 -> AnnotationType.HIGHLIGHT; 1 -> AnnotationType.UNDERLINE; 2 -> AnnotationType.NOTE
                    else -> return@setItems
                }
                val color = when (which) {
                    0 -> 0x40FFEB3B.toInt(); 1 -> 0xFFF44336.toInt(); 2 -> 0x80FF9800.toInt()
                    else -> 0
                }
                lifecycleScope.launch {
                    try {
                        val position = mapScreenToPdf(screenRect)
                        val ann = Annotation(pdfUri = pdfUri?.toString() ?: "", pageNumber = position.pageIndex,
                            rectLeft = position.pageRect.left, rectTop = position.pageRect.top,
                            rectRight = position.pageRect.right, rectBottom = position.pageRect.bottom,
                            color = color, type = type)
                        val id = withContext(Dispatchers.IO) { annotationDao.insert(ann) }
                        if (type == AnnotationType.NOTE) showNoteEditDialog(id)
                        loadAnnotationsForPage(position.pageIndex)
                        Toast.makeText(this@PdfReaderActivity, "标注已保存", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@PdfReaderActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null).show()
    }

    private fun showNoteEditDialog(annotationId: Long) {
        val input = EditText(this).apply { hint = "输入批注文字..."; minLines = 3 }
        AlertDialog.Builder(this)
            .setTitle("批注内容").setView(input)
            .setPositiveButton("保存") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { annotationDao.updateText(annotationId, input.text.toString().trim()) }
                    loadAnnotationsForPage(pdfView.currentPage)
                }
            }
            .setNegativeButton("取消", null).show()
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
        while (chatContainer.childCount > MAX_CHAT_ITEMS) chatContainer.removeViewAt(0)
    }

    // ---- Rounded drawable helpers ----

    private fun roundedBg(color: Int, radius: Int = 12): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }

    private fun roundedBgStroke(color: Int, strokeColor: Int, radius: Int = 12): GradientDrawable =
        GradientDrawable().apply { setColor(color); setStroke(1.dp, strokeColor); cornerRadius = radius.toFloat() }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    // ---- Image handling ----

    private fun processSelectedImage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream) ?: run {
                    Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show(); return
                }
                val maxDim = 1024
                val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val s = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt(), (bitmap.height * s).toInt(), true)
                } else bitmap
                val os = ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG, 60, os)
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

    // ---- Chat bubbles ----

    private fun addImageBubble(thumbnails: List<Bitmap>, count: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 6) }
        }
        thumbnails.lastOrNull()?.let { bitmap ->
            ImageView(this).apply {
                setImageBitmap(bitmap)
                layoutParams = LinearLayout.LayoutParams(120, 120).apply { setMargins(0, 0, 6, 0) }
                scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
                background = roundedBg(WHITE, 8)
            }.also { row.addView(it) }
        }
        TextView(this).apply {
            text = "📷 $count"; textSize = 11f; setTextColor(GRAY_600)
            background = roundedBg(BLUE_50, 8)
            setPadding(8, 4, 8, 4)
        }.also { row.addView(it) }
        chatContainer.addView(row); trimChatItems(); scrollDown()
    }

    private fun addUserBubble(message: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(24, 0, 0, 6) }
        }
        TextView(this).apply {
            text = message; textSize = 13f; setTextColor(GRAY_900); setPadding(12, 8, 12, 8)
            background = roundedBg(BLUE_50, 12)
            maxWidth = (resources.displayMetrics.widthPixels * 0.28).toInt()
        }.also { row.addView(it) }
        chatContainer.addView(row); trimChatItems(); scrollDown()
    }

    private fun addAiBubble(message: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 24, 6) }
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        TextView(this).apply {
            text = message; textSize = 13f; setTextColor(GRAY_900); setPadding(12, 8, 12, 8)
            background = roundedBgStroke(WHITE, GRAY_200, 12)
        }.also { col.addView(it) }
        TextView(this).apply {
            text = "保存为笔记"; textSize = 11f; setTextColor(BLUE_500)
            setPadding(4, 4, 4, 0)
            setOnClickListener {
                viewModel.saveLastResponseAsNote()
                Toast.makeText(this@PdfReaderActivity, "已保存到笔记", Toast.LENGTH_SHORT).show()
            }
        }.also { col.addView(it) }
        row.addView(col)
        chatContainer.addView(row); trimChatItems(); scrollDown()
    }

    private fun scrollDown() { scrollView.postDelayed({ scrollView.fullScroll(View.FOCUS_DOWN) }, 50) }
}
