package com.terefal.pdfaireader.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SearchAgent {

    suspend fun searchAndAugment(provider: AiProvider, question: String): String = withContext(Dispatchers.IO) {
        try {
            // Step 1: Generate a concise search query from the user's question
            val searchQuery = provider.askQuestion(
                context = "",
                question = "将以下问题转换为简洁的搜索引擎查询词（5个词以内，只返回查询词不要解释）: $question",
                images = emptyList(),
                enableWebSearch = false,
                webSearchContext = ""
            ).trim().take(50)

            // Step 2: Search the web
            val results = WebSearchService.search(searchQuery)

            if (results.isEmpty()) {
                return@withContext "[联网搜索结果为空]"
            }

            // Step 3: Summarize results
            val summary = provider.askQuestion(
                context = "",
                question = "将以下搜索结果总结为简洁的背景信息（200字以内）:\n$results",
                images = emptyList(),
                enableWebSearch = false,
                webSearchContext = ""
            ).trim()

            "搜索词: $searchQuery\n摘要: $summary\n原始结果: $results"
        } catch (e: Exception) {
            "[联网搜索失败: ${e.message}]"
        }
    }
}
