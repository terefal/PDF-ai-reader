package com.terefal.pdfaireader.ai

import android.util.Log

class GeminiProvider : AiProvider {

    private var apiKey: String = ""

    override fun initialize(apiKey: String): Boolean {
        this.apiKey = apiKey
        Log.d("GeminiProvider", "Gemini initialized with key: $apiKey")
        return true
    }

    override fun askQuestion(question: String): String {
        // Simulate Gemini API call (network implementation pending)
        return "[Gemini] Answered: $question"
    }
}