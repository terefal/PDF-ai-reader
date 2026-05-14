package com.terefal.pdfaireader.chat

import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import io.noties.markwon.Markwon
import io.noties.markwon.image.glide.GlideImagesPlugin

object MarkdownRenderer {

    private var markwon: Markwon? = null
    private var initAttempted = false

    fun init(context: Context) {
        if (initAttempted) return
        initAttempted = true
        try {
            markwon = Markwon.builder(context)
                .usePlugin(GlideImagesPlugin.create(context))
                .build()
            Log.d("MarkdownRenderer", "Markwon + GlideImages initialized")
        } catch (e: Exception) {
            Log.e("MarkdownRenderer", "Markwon init failed: ${e.message}", e)
            try { markwon = Markwon.create(context) } catch (e2: Exception) { markwon = null }
        }
    }

    fun createRenderedView(context: Context, markdown: String): TextView {
        val processed = replaceLatexWithImages(markdown)
        val tv = TextView(context).apply {
            textSize = 13f
            setTextColor(0xFF1F2328.toInt())
            setPadding(12, 8, 12, 8)
            movementMethod = LinkMovementMethod.getInstance()
        }
        val mw = markwon
        if (mw != null) {
            mw.setMarkdown(tv, processed)
        } else {
            tv.text = processed
        }
        return tv
    }

    private fun replaceLatexWithImages(text: String): String {
        var result = text
        // Block math: $$...$$ → image
        val blockRegex = Regex("""\$\$(.+?)\$\$""", RegexOption.DOT_MATCHES_ALL)
        result = blockRegex.replace(result) { match ->
            val formula = match.groupValues[1].trim()
            val url = latexToUrl(formula, large = true)
            "\n![]($url)\n"
        }
        // Inline math: $...$ → image
        val inlineRegex = Regex("""\$(.+?)\$""")
        result = inlineRegex.replace(result) { match ->
            val formula = match.groupValues[1].trim()
            val url = latexToUrl(formula, large = false)
            "![]($url)"
        }
        return result
    }

    private fun latexToUrl(formula: String, large: Boolean): String {
        val dpi = if (large) 200 else 150
        val encoded = java.net.URLEncoder.encode(formula, "UTF-8")
            .replace("+", "%20")
        return "https://latex.codecogs.com/png.image?\\dpi{$dpi}\\bg{white}$encoded"
    }
}
