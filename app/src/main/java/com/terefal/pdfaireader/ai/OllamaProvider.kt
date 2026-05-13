package com.terefal.pdfaireader.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class OllamaProvider : AiProvider {

    override val providerType: ProviderType = ProviderType.OLLAMA
    private var baseUrl: String = "http://localhost:11434"
    private var modelName: String = "llama3"

    override suspend fun initialize(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        // Ollama doesn't use API keys; apiKey parameter holds the base URL optionally
        if (apiKey.isNotBlank() && apiKey.startsWith("http")) {
            baseUrl = apiKey
        }
        Log.d("OllamaProvider", "Ollama initialized: $baseUrl")
        true
    }

    override suspend fun askQuestion(context: String, question: String): String = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl}/api/generate")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 60_000

            val prompt = "你是一个PDF阅读助手。\n文档内容:\n$context\n\n用户问题: $question\n\n请用中文回答。"
            val body = JSONObject().apply {
                put("model", modelName)
                put("prompt", prompt)
                put("stream", false)
            }

            connection.outputStream.use { os ->
                os.write(body.toString().toByteArray())
            }

            if (connection.responseCode != 200) {
                return@withContext "[错误] Ollama 未响应，请确认已启动 Ollama 服务"
            }

            val response = connection.inputStream.bufferedReader().readText()
            JSONObject(response).getString("response").trim()
        } finally {
            connection.disconnect()
        }
    }
}
