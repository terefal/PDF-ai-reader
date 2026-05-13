package com.terefal.pdfaireader.ai

import android.util.Log

class OpenAiProvider : AiProvider {

    private var apiKey: String = ""

    override fun initialize(apiKey: String): Boolean {
        this.apiKey = apiKey
        Log.d("OpenAiProvider", "OpenAI initialized with key: $apiKey")
        return true
    }

    override fun askQuestion(question: String): String {
        // Simulate OpenAI API call (network implementation pending)
        return "[OpenAI] Answered: $question"
    }
}