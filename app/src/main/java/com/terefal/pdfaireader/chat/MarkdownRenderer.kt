package com.terefal.pdfaireader.chat

import android.content.Context
import android.text.method.LinkMovementMethod
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin

object MarkdownRenderer {

    private var markwon: Markwon? = null

    fun init(context: Context) {
        if (markwon != null) return
        markwon = Markwon.builder(context)
            .usePlugin(JLatexMathPlugin.create(context.resources.displayMetrics.density.toInt()) { builder ->
                builder.inlinesEnabled(true)
                builder.blocksEnabled(true)
            })
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .build()
    }

    fun createRenderedView(context: Context, markdown: String): TextView {
        val tv = TextView(context).apply {
            textSize = 13f
            setTextColor(0xFF1F2328.toInt())
            setPadding(12, 8, 12, 8)
            movementMethod = LinkMovementMethod.getInstance()
        }
        markwon?.setMarkdown(tv, markdown) ?: tv.apply { text = markdown }
        return tv
    }
}
