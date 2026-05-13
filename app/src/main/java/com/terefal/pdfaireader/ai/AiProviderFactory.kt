package com.terefal.pdfaireader.ai

class AiProviderFactory {

    companion object {
        fun create(type: ProviderType): AiProvider {
            return when (type) {
                ProviderType.DEEPSEEK -> DeepSeekProvider()
                ProviderType.OPENAI -> OpenAiCompatProvider(
                    providerType = ProviderType.OPENAI,
                    baseUrl = "https://api.openai.com/v1/",
                    modelName = "gpt-3.5-turbo",
                    useProxy = true
                )
                ProviderType.OLLAMA -> OllamaProvider()
            }
        }
    }
}
