package com.terefal.pdfaireader.config

import android.content.Context
import com.terefal.pdfaireader.ai.ProviderType

class SettingsManager(context: Context) {

    private val prefs = context.getSharedPreferences("pdf_ai_reader", Context.MODE_PRIVATE)

    var currentProvider: ProviderType
        get() {
            val name = prefs.getString("current_provider", ProviderType.DEEPSEEK.name) ?: ProviderType.DEEPSEEK.name
            return try { ProviderType.valueOf(name) } catch (_: Exception) { ProviderType.DEEPSEEK }
        }
        set(value) = prefs.edit().putString("current_provider", value.name).apply()

    var deepseekApiKey: String
        get() = prefs.getString("deepseek_key", "") ?: ""
        set(value) = prefs.edit().putString("deepseek_key", value).apply()

    var openaiApiKey: String
        get() = prefs.getString("openai_key", "") ?: ""
        set(value) = prefs.edit().putString("openai_key", value).apply()

    var ollamaUrl: String
        get() = prefs.getString("ollama_url", "http://localhost:11434") ?: "http://localhost:11434"
        set(value) = prefs.edit().putString("ollama_url", value).apply()

    fun getApiKeyFor(provider: ProviderType): String = when (provider) {
        ProviderType.DEEPSEEK -> deepseekApiKey
        ProviderType.OPENAI -> openaiApiKey
        ProviderType.OLLAMA -> ollamaUrl
    }
}
