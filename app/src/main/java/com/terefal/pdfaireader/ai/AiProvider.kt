package com.terefal.pdfaireader.ai

/**
 * Abstract AiProvider interface for handling multiple AI models.
 */
interface AiProvider {
    fun initialize(apiKey: String): Boolean
    fun askQuestion(question: String): String
}