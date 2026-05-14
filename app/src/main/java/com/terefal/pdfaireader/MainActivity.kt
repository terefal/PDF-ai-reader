package com.terefal.pdfaireader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.terefal.pdfaireader.data.AppDatabase
import com.terefal.pdfaireader.data.NoteBook
import com.terefal.pdfaireader.view.FileTreeAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var filePickerLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private val db by lazy { AppDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.newNoteButton).setOnClickListener {
            lifecycleScope.launch {
                val id = withContext(Dispatchers.IO) { db.noteBookDao().insert(NoteBook(title = "新建笔记")) }
                openNoteBook(id)
            }
        }

        findViewById<TextView>(R.id.importPdfButton).setOnClickListener { openFilePicker() }

        findViewById<TextView>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val recentList = findViewById<RecyclerView>(R.id.recentNotesList)
        recentList.layoutManager = LinearLayoutManager(this)
        val adapter = FileTreeAdapter(
            onNoteBookClick = { nb -> openNoteBook(nb.id) },
            onNoteBookDelete = { nb ->
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("删除").setMessage("确定删除「${nb.title}」？")
                    .setPositiveButton("删除") { _, _ -> lifecycleScope.launch { withContext(Dispatchers.IO) { db.noteBookDao().delete(nb) } } }
                    .setNegativeButton("取消", null).show()
            }
        )
        recentList.adapter = adapter
        lifecycleScope.launch { db.noteBookDao().getAllNoteBooks().collect { adapter.submitList(it) } }

        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                result.data?.data?.let { uri ->
                    lifecycleScope.launch {
                        val fileName = uri.lastPathSegment ?: "PDF文档"
                        val id = withContext(Dispatchers.IO) {
                            db.noteBookDao().insert(NoteBook(title = fileName, pdfUri = uri.toString(), pdfFileName = fileName))
                        }
                        openNoteBook(id)
                    }
                } ?: Toast.makeText(this, "未选择文件", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openNoteBook(id: Long) {
        startActivity(Intent(this, PdfReaderActivity::class.java).apply { putExtra(PdfReaderActivity.EXTRA_NOTEBOOK_ID, id) })
    }

    private fun openFilePicker() {
        filePickerLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/pdf"; addCategory(Intent.CATEGORY_OPENABLE)
        })
    }
}
