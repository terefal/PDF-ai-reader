package com.terefal.pdfaireader.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.terefal.pdfaireader.ai.AiProvider
import com.terefal.pdfaireader.ai.AiProviderFactory
import com.terefal.pdfaireader.ai.ChatImage
import com.terefal.pdfaireader.ai.SearchAgent
import com.terefal.pdfaireader.config.SettingsManager
import com.terefal.pdfaireader.data.AppDatabase
import com.terefal.pdfaireader.data.Note
import com.terefal.pdfaireader.pdf.PdfTextExtractor
import kotlinx.coroutines.launch

class PdfReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val _aiResponse = MutableLiveData<String>()
    val aiResponse: LiveData<String> = _aiResponse

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _pdfContext = MutableLiveData<String>()
    val pdfContext: LiveData<String> = _pdfContext

    private var aiProvider: AiProvider? = null
    private var pdfUri: Uri? = null
    private var lastQuestion: String = ""
    private var lastAnswer: String = ""
    var pendingImages: List<ChatImage> = emptyList()

    private val noteDao = AppDatabase.getInstance(application).noteDao()

    fun initProvider(settings: SettingsManager) {
        val providerType = settings.currentProvider
        val apiKey = settings.getApiKeyFor(providerType)
        aiProvider = AiProviderFactory.create(providerType)
        viewModelScope.launch {
            aiProvider?.initialize(apiKey)
        }
    }

    fun loadPdf(contentResolver: ContentResolver, uri: Uri) {
        this.pdfUri = uri
        _pdfContext.value = ""
        viewModelScope.launch {
            val text = PdfTextExtractor.extractText(contentResolver, uri)
            _pdfContext.value = text
        }
    }

    fun queryAi(question: String, images: List<ChatImage> = emptyList(), enableWebSearch: Boolean = false) {
        val provider = aiProvider
        if (provider == null) {
            _aiResponse.value = "[错误] AI Provider 未初始化，请先在设置中配置"
            return
        }
        if (question.isBlank()) return

        lastQuestion = question
        pendingImages = emptyList()
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val context = _pdfContext.value ?: ""
                var webSearchContext = ""

                if (enableWebSearch) {
                    webSearchContext = SearchAgent.searchAndAugment(provider, question)
                }

                val response = provider.askQuestion(
                    context = context,
                    question = question,
                    images = images,
                    enableWebSearch = enableWebSearch,
                    webSearchContext = webSearchContext
                )
                _aiResponse.value = response
                lastAnswer = response
            } catch (e: Exception) {
                _aiResponse.value = "[错误] ${e.message}"
                lastAnswer = "[错误] ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveLastResponseAsNote() {
        if (lastAnswer.isNotEmpty()) {
            viewModelScope.launch {
                noteDao.insert(
                    Note(
                        pdfUri = pdfUri?.toString() ?: "",
                        question = lastQuestion,
                        content = lastAnswer
                    )
                )
            }
        }
    }
}
