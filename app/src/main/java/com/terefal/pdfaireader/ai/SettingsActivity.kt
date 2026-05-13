package com.terefal.pdfaireader.ai

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import com.terefal.pdfaireader.R
import com.terefal.pdfaireader.config.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsManager
    private lateinit var providerSpinner: Spinner
    private lateinit var deepseekKeyInput: EditText
    private lateinit var openaiKeyInput: EditText
    private lateinit var ollamaUrlInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settings = SettingsManager(this)
        providerSpinner = findViewById(R.id.providerSpinner)
        deepseekKeyInput = findViewById(R.id.deepseekKeyInput)
        openaiKeyInput = findViewById(R.id.openaiKeyInput)
        ollamaUrlInput = findViewById(R.id.ollamaUrlInput)
        statusText = findViewById(R.id.statusText)

        // Setup spinner
        val providers = ProviderType.values().map { it.displayName }
        providerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, providers).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        providerSpinner.setSelection(settings.currentProvider.ordinal)

        providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val type = ProviderType.values()[pos]
                settings.currentProvider = type
                updateKeyVisibility(type)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Load saved values
        deepseekKeyInput.setText(settings.deepseekApiKey)
        openaiKeyInput.setText(settings.openaiApiKey)
        ollamaUrlInput.setText(settings.ollamaUrl)
        updateKeyVisibility(settings.currentProvider)

        // Save button
        findViewById<Button>(R.id.saveButton).setOnClickListener {
            settings.deepseekApiKey = deepseekKeyInput.text.toString().trim()
            settings.openaiApiKey = openaiKeyInput.text.toString().trim()
            settings.ollamaUrl = ollamaUrlInput.text.toString().trim()
            statusText.text = "设置已保存"
        }

        // Test button
        findViewById<Button>(R.id.testButton).setOnClickListener {
            statusText.text = "测试中..."
            val provider = AiProviderFactory.create(settings.currentProvider, settings.getApiKeyFor(settings.currentProvider))
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    withContext(Dispatchers.IO) { provider.initialize(settings.getApiKeyFor(settings.currentProvider)) }
                    val response = withContext(Dispatchers.IO) {
                        provider.askQuestion("", "你好，请回复'连接成功'")
                    }
                    statusText.text = if (response.contains("成功")) "连接成功" else "响应: ${response.take(50)}..."
                } catch (e: Exception) {
                    statusText.text = "连接失败: ${e.message}"
                }
            }
        }
    }

    private fun updateKeyVisibility(type: ProviderType) {
        deepseekKeyInput.visibility = if (type == ProviderType.DEEPSEEK) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.deepseekLabel).visibility = deepseekKeyInput.visibility

        openaiKeyInput.visibility = if (type == ProviderType.OPENAI) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.openaiLabel).visibility = openaiKeyInput.visibility

        ollamaUrlInput.visibility = if (type == ProviderType.OLLAMA) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.ollamaLabel).visibility = ollamaUrlInput.visibility
    }
}
