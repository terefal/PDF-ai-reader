package com.terefal.pdfaireader.ai

class DeepSeekProvider : OpenAiCompatProvider(
    providerType = ProviderType.DEEPSEEK,
    baseUrl = "https://api.deepseek.com/v1/",
    modelName = "deepseek-chat"
)
