package com.terefal.pdfaireader.chat

import java.util.UUID

enum class TagType { SELECTION, IMAGE, PDF_PAGE }

data class ContextTag(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val content: String,
    val tagType: TagType
)
