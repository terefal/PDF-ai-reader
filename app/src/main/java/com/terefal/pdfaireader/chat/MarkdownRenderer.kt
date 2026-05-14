package com.terefal.pdfaireader.chat

import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.TextView
import io.noties.markwon.Markwon

object MarkdownRenderer {

    private var markwon: Markwon? = null
    private var initAttempted = false

    fun init(context: Context) {
        if (initAttempted) return
        initAttempted = true
        try {
            markwon = Markwon.builder(context)
                .usePlugin(io.noties.markwon.ext.latex.JLatexMathPlugin.create(16f) { builder ->
                    builder.inlinesEnabled(true)
                    builder.blocksEnabled(true)
                })
                .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
                .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(context))
                .build()
            Log.d("MarkdownRenderer", "Markwon + LaTeX initialized")
        } catch (e: Exception) {
            Log.e("MarkdownRenderer", "Markwon init failed: ${e.message}", e)
            try { markwon = Markwon.create(context); Log.d("MarkdownRenderer", "Fallback to basic Markwon") }
            catch (e2: Exception) { markwon = null }
        }
    }

    fun createRenderedView(context: Context, markdown: String): TextView {
        val tv = TextView(context).apply {
            textSize = 13f
            setTextColor(0xFF1F2328.toInt())
            setPadding(12, 8, 12, 8)
            movementMethod = LinkMovementMethod.getInstance()
        }
        val mw = markwon
        if (mw != null) {
            mw.setMarkdown(tv, markdown)
        } else {
            tv.text = markdown
        }
        return tv
    }
}
