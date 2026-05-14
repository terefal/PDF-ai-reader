package com.terefal.pdfaireader.chat

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

class ContextTagManager(
    private val tagContainer: LinearLayout,
    private val onTagsChanged: (List<ContextTag>) -> Unit
) {
    private val tags = mutableListOf<ContextTag>()

    fun addTag(tag: ContextTag) {
        tags.add(tag)
        tagContainer.addView(createChipView(tag))
        tagContainer.visibility = View.VISIBLE
        onTagsChanged(tags)
    }

    fun removeTag(tagId: String) {
        tags.removeAll { it.id == tagId }
        rebuildViews()
        if (tags.isEmpty()) tagContainer.visibility = View.GONE
        onTagsChanged(tags)
    }

    fun getTags(): List<ContextTag> = tags.toList()
    fun hasTags(): Boolean = tags.isNotEmpty()
    fun clearTags() {
        tags.clear()
        tagContainer.removeAllViews()
        tagContainer.visibility = View.GONE
        onTagsChanged(emptyList())
    }

    fun buildContextPrompt(): String {
        if (tags.isEmpty()) return ""
        return tags.joinToString("\n\n") { tag ->
            when (tag.tagType) {
                TagType.SELECTION -> "[${tag.label}]\n${tag.content}"
                TagType.IMAGE -> "[图片] ${tag.label}"
                TagType.PDF_PAGE -> "[第${tag.label}页全文]"
            }
        }
    }

    private fun createChipView(tag: ContextTag): View {
        val ctx = tagContainer.context
        val chip = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 6.dp(ctx), 0) }
            background = GradientDrawable().apply {
                setColor(0xFFDDF4FF.toInt())
                cornerRadius = 12.dp(ctx).toFloat()
            }
            setPadding(8.dp(ctx), 4.dp(ctx), 8.dp(ctx), 4.dp(ctx))
        }

        val label = tag.label.take(15).let { if (tag.label.length > 15) "$it..." else it }
        val labelTv = TextView(ctx).apply {
            text = label; textSize = 11f
            setTextColor(0xFF1F2328.toInt())
            maxWidth = 120.dp(ctx)
            isSingleLine = true
        }
        chip.addView(labelTv)

        val closeBtn = TextView(ctx).apply {
            text = " ✕"; textSize = 11f
            setTextColor(0xFF656D76.toInt())
            setOnClickListener { removeTag(tag.id) }
        }
        chip.addView(closeBtn)

        return chip
    }

    private fun rebuildViews() {
        tagContainer.removeAllViews()
        tags.forEach { tagContainer.addView(createChipView(it)) }
    }

    private fun Int.dp(ctx: Context): Int = (this * ctx.resources.displayMetrics.density).toInt()
}
