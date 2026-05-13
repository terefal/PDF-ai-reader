package com.terefal.pdfaireader.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

open class OpenAiCompatProvider(
    override val providerType: ProviderType,
    private val baseUrl: String,
    private val modelName: String,
    private val useProxy: Boolean = false
) : AiProvider {

    protected var apiKey: String = ""

    override suspend fun initialize(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        this@OpenAiCompatProvider.apiKey = apiKey
        Log.d("AiProvider", "${providerType.name} initialized")
        true
    }

    override suspend fun askQuestion(context: String, question: String): String = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl}chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000

            val systemPrompt = "你是一个专业的PDF文档阅读助手。根据提供的文档内容，准确、简洁地回答用户问题。"
            val userMessage = "文档内容:\n$context\n\n用户问题: $question"

            val body = JSONObject().apply {
                put("model", modelName)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                })
                put("temperature", 0.3)
                put("max_tokens", 2000)
            }

            connection.outputStream.use { os ->
                os.write(body.toString().toByteArray())
            }

            if (connection.responseCode != 200) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e("AiProvider", "${providerType.name} API error: ${connection.responseCode} $errorBody")
                return@withContext "[错误] API请求失败 (${connection.responseCode})"
            }

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val choices = json.getJSONArray("choices")
            val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
            content.trim()
        } finally {
            connection.disconnect()
        }
    }
}