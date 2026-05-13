package com.terefal.pdfaireader.ai

data class ChatImage(val base64: String, val mimeType: String = "image/png")

interface AiProvider {
    val providerType: ProviderType
    suspend fun initialize(apiKey: String): Boolean
    suspend fun askQuestion(
        context: String,
        question: String,
        images: List<ChatImage> = emptyList(),
        enableWebSearch: Boolean = false,
        webSearchContext: String = ""
    ): String
}
