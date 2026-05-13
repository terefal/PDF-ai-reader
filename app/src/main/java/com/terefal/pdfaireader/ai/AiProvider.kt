package com.terefal.pdfaireader.ai

interface AiProvider {
    val providerType: ProviderType
    suspend fun initialize(apiKey: String): Boolean
    suspend fun askQuestion(context: String, question: String): String
}