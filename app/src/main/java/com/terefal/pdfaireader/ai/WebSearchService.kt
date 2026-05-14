package com.terefal.pdfaireader.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object WebSearchService {

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = URL("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "PDF-AI-Reader/1.0")

            if (connection.responseCode != 200) return@withContext ""

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)

            buildString {
                val abstract = json.optString("Abstract", "")
                if (abstract.isNotEmpty()) {
                    append(abstract)
                    append("\n\n")
                }
                val topics = json.optJSONArray("RelatedTopics")
                if (topics != null) {
                    for (i in 0 until minOf(topics.length(), 5)) {
                        val text = topics.getJSONObject(i).optString("Text", "")
                        if (text.isNotEmpty()) append("- $text\n")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WebSearchService", "Search failed: ${e.message}")
            ""
        } finally {
            connection.disconnect()
        }
    }
}
