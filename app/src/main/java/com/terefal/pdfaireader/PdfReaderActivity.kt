package com.terefal.pdfaireader

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.barteksc.pdfviewer.PDFView
import com.terefal.pdfaireader.ai.ChatImage
import com.terefal.pdfaireader.chat.ContextTag
import com.terefal.pdfaireader.chat.ContextTagManager
import com.terefal.pdfaireader.chat.MarkdownRenderer
import com.terefal.pdfaireader.chat.TagType
import com.terefal.pdfaireader.config.SettingsManager
import com.terefal.pdfaireader.data.Annotation
import com.terefal.pdfaireader.data.AnnotationType
import com.terefal.pdfaireader.data.AppDatabase
import com.terefal.pdfaireader.data.NoteBook
import com.terefal.pdfaireader.pdf.AnnotationOverlay
import com.terefal.pdfaireader.pdf.PdfCoordinateMapper
import com.terefal.pdfaireader.pdf.PdfTextExtractor
import com.terefal.pdfaireader.pdf.SelectionOverlay
import com.terefal.pdfaireader.view.FileTreeAdapter
import com.terefal.pdfaireader.viewmodel.PdfReaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class PdfReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTEBOOK_ID = "NOTEBOOK_ID"
        private const val MAX_CHAT_ITEMS = 40
        private val BLUE_500 = 0xFF0969DA.toInt()
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
    private lateinit var notebookTitleEdit: EditText
    private lateinit var contextTagManager: ContextTagManager

    private var currentImages: MutableList<ChatImage> = mutableListOf()
    private var currentThumbnails: MutableList<Bitmap> = mutableListOf()
    private var pdfUri: Uri? = null
    private var currentNoteBookId: Long = 0
    private var currentNoteBook: NoteBook? = null

    private lateinit var selectionOverlay: SelectionOverlay
    private var isCircleSelectMode = false

    private lateinit var annotationOverlay: AnnotationOverlay
    private var isAnnotationMode = false
    private var annotationCollectJob: Job? = null
    private val db by lazy { AppDatabase.getInstance(this) }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processSelectedImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_reader)
        MarkdownRenderer.init(this)

        pdfView = findViewById(R.id.pdfView)
        titlePageInfo = findViewById(R.id.titlePageInfo)
        notebookTitleEdit = findViewById(R.id.notebookTitleEdit)
        currentNoteBookId = intent.getLongExtra(EXTRA_NOTEBOOK_ID, 0)

        PdfTextExtractor.init(applicationContext)

        lifecycleScope.launch {
            val nb = withContext(Dispatchers.IO) { db.noteBookDao().getNoteBookById(currentNoteBookId) }
            if (nb != null) {
                currentNoteBook = nb
                notebookTitleEdit.setText(nb.title)
                if (!nb.pdfUri.isNullOrEmpty()) {
                    pdfUri = Uri.parse(nb.pdfUri)
                    val u = pdfUri ?: return@launch
                    pdfView.fromUri(u).enableSwipe(true).swipeHorizontal(false).enableDoubletap(true).load()
                    viewModel.loadPdf(contentResolver, pdfUri!!)
                } else {
                    titlePageInfo.text = "空白笔记"
                }
            }
        }

        // Overlays
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
        viewModel.setNoteBookId(currentNoteBookId)

        // Sidebar
        chatContainer = findViewById(R.id.chatContainer)
        questionInput = findViewById(R.id.questionInput)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        scrollView = findViewById(R.id.chatScrollView)
        webSearchSwitch = findViewById(R.id.webSearchSwitch)
        sendButton = findViewById(R.id.sendButton)
        circleChip = findViewById(R.id.circleChip)
        annotateChip = findViewById(R.id.annotateChip)

        // Context tags
        val tagContainer = findViewById<LinearLayout>(R.id.contextTagContainer)
        val tagScroll = findViewById<HorizontalScrollView>(R.id.contextTagScroll)
        contextTagManager = ContextTagManager(tagContainer) { tags ->
            tagScroll.visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE
        }

        // File tree
        val fileTreeRecycler = findViewById<RecyclerView>(R.id.fileTreeRecycler)
        fileTreeRecycler.layoutManager = LinearLayoutManager(this)
        val fileTreeAdapter = FileTreeAdapter(
            onNoteBookClick = { nb -> openNoteBook(nb.id) },
            onNoteBookDelete = { nb ->
                AlertDialog.Builder(this@PdfReaderActivity)
                    .setTitle("删除笔记")
                    .setMessage("确定删除「${nb.title}」？")
                    .setPositiveButton("删除") { _, _ -> lifecycleScope.launch { withContext(Dispatchers.IO) { db.noteBookDao().delete(nb) } } }
                    .setNegativeButton("取消", null).show()
            }
        )
        fileTreeRecycler.adapter = fileTreeAdapter
        lifecycleScope.launch {
            db.noteBookDao().getAllNoteBooks().collect { fileTreeAdapter.submitList(it) }
        }

        // New note button
        findViewById<TextView>(R.id.newNoteButton).setOnClickListener { createNewNote() }

        // Back button
        findViewById<TextView>(R.id.backButton).setOnClickListener { finish() }

        // Title editing auto-save
        notebookTitleEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveTitle()
        }
        notebookTitleEdit.setOnEditorActionListener { _, _, _ -> saveTitle(); true }

        // Image & send
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
            sendButton.isEnabled = !loading; sendButton.alpha = if (loading) 0.5f else 1f
        }
        viewModel.pdfContext.observe(this) { ctx ->
            if (ctx.isNotEmpty()) Toast.makeText(this, "PDF 文本已提取 (${ctx.length} 字符)", Toast.LENGTH_SHORT).show()
        }

        loadAnnotationsForPage(pdfView.currentPage)
    }

    private fun saveTitle() {
        val title = notebookTitleEdit.text.toString().trim().ifBlank { "无标题笔记" }
        lifecycleScope.launch { withContext(Dispatchers.IO) { db.noteBookDao().updateTitle(currentNoteBookId, title) } }
    }

    private fun createNewNote() {
        lifecycleScope.launch {
            val id = withContext(Dispatchers.IO) {
                db.noteBookDao().insert(NoteBook(title = "新建笔记"))
            }
            openNoteBook(id)
            Toast.makeText(this@PdfReaderActivity, "已创建新笔记", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openNoteBook(id: Long) {
        if (id == currentNoteBookId) return
        annotationCollectJob?.cancel()
        val intent = android.content.Intent(this, PdfReaderActivity::class.java).apply {
            putExtra(EXTRA_NOTEBOOK_ID, id)
        }
        startActivity(intent)
        finish()
    }

    private fun sendMessage() {
        val userText = questionInput.text.toString().trim()
        val tags = contextTagManager.getTags()
        if (userText.isEmpty() && tags.isEmpty() && currentImages.isEmpty()) return

        val contextPrompt = contextTagManager.buildContextPrompt()
        val assembled = if (contextPrompt.isNotEmpty()) "$userText\n\n--- 上下文引用 ---\n$contextPrompt" else userText

        if (currentImages.isNotEmpty()) addImageBubble(currentThumbnails, currentImages.size)
        if (userText.isNotEmpty() || tags.isNotEmpty()) addUserBubble(userText, tags)
        questionInput.text.clear()

        val images = currentImages.toList(); currentImages.clear(); currentThumbnails.clear()
        contextTagManager.clearTags()
        viewModel.queryAi(assembled, images, webSearchSwitch.isChecked)
    }

    private fun setToolMode(circleSelect: Boolean, annotate: Boolean) {
        isCircleSelectMode = circleSelect
        isAnnotationMode = annotate
        selectionOverlay.isSelectionEnabled = circleSelect || annotate
        fun applyChip(chip: TextView, active: Boolean) {
            chip.background = if (active) ContextCompat.getDrawable(this, R.drawable.chip_active) else ContextCompat.getDrawable(this, R.drawable.chip_inactive)
            chip.setTextColor(if (active) WHITE else GRAY_900)
        }
        applyChip(circleChip, circleSelect); applyChip(annotateChip, annotate)
    }

    private suspend fun mapScreenToPdf(screenRect: Rect) = withContext(Dispatchers.IO) { PdfCoordinateMapper.screenToPdf(pdfView, screenRect) }

    private fun handleSelectionComplete(screenRect: Rect) {
        if (isCircleSelectMode) {
            lifecycleScope.launch {
                try {
                    val position = mapScreenToPdf(screenRect)
                    val uri = pdfUri ?: throw IllegalStateException("No PDF loaded")
                    val text = withContext(Dispatchers.IO) {
                        PdfTextExtractor.extractTextByArea(contentResolver, uri, position.pageIndex + 1, position.pageRect)
                    }
                    contextTagManager.addTag(ContextTag(
                        label = "第${position.pageIndex + 1}页",
                        content = text,
                        tagType = TagType.SELECTION
                    ))
                    Toast.makeText(this@PdfReaderActivity, "已添加上下文标签 (第${position.pageIndex + 1}页)", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@PdfReaderActivity, "提取失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (isAnnotationMode) {
            showAnnotationPopup(screenRect)
        }
    }

    private fun showAnnotationPopup(screenRect: Rect) {
        AlertDialog.Builder(this).setTitle("标注类型")
            .setItems(arrayOf("🟡 高亮", "➖ 下划线", "📝 批注")) { _, which ->
                val type = when (which) { 0 -> AnnotationType.HIGHLIGHT; 1 -> AnnotationType.UNDERLINE; 2 -> AnnotationType.NOTE; else -> return@setItems }
                val color = when (which) { 0 -> 0x40FFEB3B.toInt(); 1 -> 0xFFF44336.toInt(); 2 -> 0x80FF9800.toInt(); else -> 0 }
                lifecycleScope.launch {
                    try {
                        val position = mapScreenToPdf(screenRect)
                        val ann = Annotation(noteBookId = currentNoteBookId, pdfUri = pdfUri?.toString() ?: "",
                            pageNumber = position.pageIndex, rectLeft = position.pageRect.left, rectTop = position.pageRect.top,
                            rectRight = position.pageRect.right, rectBottom = position.pageRect.bottom, color = color, type = type)
                        val id = withContext(Dispatchers.IO) { db.annotationDao().insert(ann) }
                        if (type == AnnotationType.NOTE) showNoteEditDialog(id)
                        loadAnnotationsForPage(position.pageIndex)
                        Toast.makeText(this@PdfReaderActivity, "标注已保存", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@PdfReaderActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.setNegativeButton("取消", null).show()
    }

    private fun showNoteEditDialog(annotationId: Long) {
        val input = EditText(this).apply { hint = "输入批注文字..."; minLines = 3 }
        AlertDialog.Builder(this).setTitle("批注内容").setView(input)
            .setPositiveButton("保存") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { db.annotationDao().updateText(annotationId, input.text.toString().trim()) }
                    loadAnnotationsForPage(pdfView.currentPage)
                }
            }.setNegativeButton("取消", null).show()
    }

    private fun loadAnnotationsForPage(page: Int) {
        annotationCollectJob?.cancel()
        annotationCollectJob = lifecycleScope.launch {
            db.annotationDao().getAnnotationsForNoteBookPage(currentNoteBookId, page).collect { list ->
                annotationOverlay.setAnnotations(list, page)
            }
        }
    }

    private fun trimChatItems() { while (chatContainer.childCount > MAX_CHAT_ITEMS) chatContainer.removeViewAt(0) }

    private fun roundedBg(color: Int, radius: Int = 12) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private fun roundedBgStroke(color: Int, strokeColor: Int, radius: Int = 12) = GradientDrawable().apply { setColor(color); setStroke(1, strokeColor); cornerRadius = radius.toFloat() }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    // --- Image ---
    private fun processSelectedImage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream) ?: run { Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show(); return }
                val maxDim = 1024
                val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val s = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt(), (bitmap.height * s).toInt(), true)
                } else bitmap
                val os = ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG, 60, os)
                val b64 = android.util.Base64.encodeToString(os.toByteArray(), android.util.Base64.NO_WRAP)
                currentImages.add(ChatImage(base64 = b64, mimeType = "image/jpeg"))
                currentThumbnails.add(scaled)
                contextTagManager.addTag(ContextTag(label = "图片 ${currentImages.size}", content = "", tagType = TagType.IMAGE))
                addImageBubble(currentThumbnails, currentImages.size)
                Toast.makeText(this, "图片已添加 (${currentImages.size} 张)", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { Toast.makeText(this, "图片处理失败: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    // --- Chat bubbles ---
    private fun addImageBubble(thumbnails: List<Bitmap>, count: Int) {
        val row = createBubbleRow(Gravity.END)
        thumbnails.lastOrNull()?.let { bitmap ->
            ImageView(this).apply {
                setImageBitmap(bitmap); layoutParams = LinearLayout.LayoutParams(120, 120).apply { setMargins(0, 0, 6, 0) }
                scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; background = roundedBg(WHITE, 8)
            }.also { row.addView(it) }
        }
        tv("📷 $count", 11f, GRAY_600, roundedBg(BLUE_50, 8), Gravity.CENTER).also { row.addView(it) }
        chatContainer.addView(row); trimChatItems(); scrollDown()
    }

    private fun addUserBubble(message: String, tags: List<ContextTag> = emptyList()) {
        val row = createBubbleRow(Gravity.END, marginStart = 24)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (message.isNotEmpty()) {
            tv(message, 13f, GRAY_900, roundedBg(BLUE_50, 12), Gravity.START).also { col.addView(it) }
        }
        if (tags.isNotEmpty()) {
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 2.dp }
                tags.forEach { tag ->
                    tv("📌 ${tag.label.take(10)}", 10f, GRAY_600, null, Gravity.CENTER).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 4.dp, 0) }
                    }.also { addView(it) }
                }
            }.also { col.addView(it) }
        }
        row.addView(col); chatContainer.addView(row); trimChatItems(); scrollDown()
    }

    private fun addAiBubble(message: String) {
        val row = createBubbleRow(Gravity.START, marginEnd = 24)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        MarkdownRenderer.createRenderedView(this, message).apply {
            background = roundedBgStroke(WHITE, GRAY_200, 12)
        }.also { col.addView(it) }
        tv("保存为笔记", 11f, BLUE_500, null, Gravity.START).apply {
            setPadding(4, 4, 4, 0); setOnClickListener { viewModel.saveLastResponseAsNote(); Toast.makeText(this@PdfReaderActivity, "已保存", Toast.LENGTH_SHORT).show() }
        }.also { col.addView(it) }
        row.addView(col); chatContainer.addView(row); trimChatItems(); scrollDown()
    }

    private fun tv(text: String, size: Float, color: Int, bg: GradientDrawable?, gravity: Int): TextView =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            if (bg != null) this.background = bg
            this.gravity = gravity
        }

    private fun createBubbleRow(gravity: Int, marginStart: Int = 0, marginEnd: Int = 0): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; this.gravity = gravity
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(marginStart, 0, marginEnd, 6) }
        }

    private fun scrollDown() { scrollView.postDelayed({ scrollView.fullScroll(View.FOCUS_DOWN) }, 50) }
}
