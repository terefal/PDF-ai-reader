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
    private val modelName: String
) : AiProvider {

    protected var apiKey: String = ""

    override suspend fun initialize(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        this@OpenAiCompatProvider.apiKey = apiKey
        Log.d("AiProvider", "${providerType.name} initialized")
        true
    }

    override suspend fun askQuestion(
        context: String,
        question: String,
        images: List<ChatImage>,
        enableWebSearch: Boolean,
        webSearchContext: String
    ): String = withContext(Dispatchers.IO) {
        val url = URL("${baseUrl}chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000

            val systemPrompt = buildSystemPrompt(images.isNotEmpty(), enableWebSearch, webSearchContext)
            val userContent = buildUserContent(context, question, images)

            val body = JSONObject().apply {
                put("model", modelName)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userContent)
                    })
                })
                put("temperature", 0.5)
                put("max_tokens", 3000)
            }

            connection.outputStream.use { os ->
                os.write(body.toString().toByteArray(Charsets.UTF_8))
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

    private fun buildSystemPrompt(hasImages: Boolean, enableWebSearch: Boolean, webSearchContext: String): String {
        return when {
            enableWebSearch -> buildString {
                append("你是一个全能知识助手。请结合以下联网搜索结果、你的知识和用户提供的文档，全面深入地回答问题。\n")
                if (webSearchContext.isNotEmpty()) {
                    append("联网搜索结果:\n$webSearchContext\n\n")
                }
                append("请充分展开思考，提供有深度的分析。如有需要，可以指出不同观点和信息来源。")
            }
            hasImages -> "你是一个专业的PDF文档阅读助手。根据提供的文档内容和图片，仔细分析并准确回答用户问题。如果图片中包含图表、公式或截图，请详细解读。"
            else -> "你是一个专业的PDF文档阅读助手。根据提供的文档内容，准确、简洁地回答用户问题。如果文档内容不足以回答，可以结合你的知识进行补充，但需注明哪些来自文档、哪些来自你的知识。"
        }
    }

    private fun buildUserContent(context: String, question: String, images: List<ChatImage>): Any {
        if (images.isEmpty()) {
            return "文档内容:\n$context\n\n用户问题: $question"
        }

        // Multimodal: build content array with text + images
        val contentArray = JSONArray()
        contentArray.put(JSONObject().apply {
            put("type", "text")
            put("text", "文档内容:\n$context\n\n用户问题: $question\n\n请参考以下图片进行回答。")
        })
        for (img in images) {
            contentArray.put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", "data:${img.mimeType};base64,${img.base64}")
                })
            })
        }
        return contentArray
    }
}
