package com.synapse.app.feature.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.app.domain.models.ArticleInput
import com.synapse.app.domain.models.HistoryItem
import com.synapse.app.domain.models.TransformResult
import com.synapse.app.domain.models.TransformType
import com.synapse.app.domain.models.WorkflowType
import com.synapse.app.domain.providers.ArticleTransformProvider
import com.synapse.app.domain.repositories.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ArticleUiState {
    data object Input : ArticleUiState
    data object Loading : ArticleUiState
    data class Error(val message: String) : ArticleUiState
    data class Success(val result: TransformResult) : ArticleUiState
}

@HiltViewModel
class ArticleViewModel @Inject constructor(
    private val transformProvider: ArticleTransformProvider,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArticleUiState>(ArticleUiState.Input)
    val uiState: StateFlow<ArticleUiState> = _uiState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _selectedTransform = MutableStateFlow(TransformType.SUMMARY)
    val selectedTransform: StateFlow<TransformType> = _selectedTransform.asStateFlow()

    private val _saveStatus = MutableStateFlow<String?>(null)
    val saveStatus: StateFlow<String?> = _saveStatus.asStateFlow()

    fun updateInputText(text: String) {
        _inputText.value = text
        if (_uiState.value is ArticleUiState.Error) {
            _uiState.value = ArticleUiState.Input
        }
    }

    fun selectTransform(type: TransformType) {
        _selectedTransform.value = type
    }
    
    fun setInitialTextOnce(text: String) {
        if (_inputText.value.isEmpty() && text.isNotBlank() && text != "empty") {
            _inputText.value = text
        }
    }

    fun transform() {
        val text = _inputText.value.trim()
        
        if (text.isEmpty()) {
            _uiState.value = ArticleUiState.Error("Please enter or paste some text first.")
            return
        }
        
        if (text.length < 50) {
            _uiState.value = ArticleUiState.Error("Text is too short. Please provide at least 50 characters.")
            return
        }

        _uiState.value = ArticleUiState.Loading

        viewModelScope.launch {
            val result = transformProvider.transform(
                input = ArticleInput(text = text),
                type = _selectedTransform.value
            )

            result.fold(
                onSuccess = { transformResult ->
                    _uiState.value = ArticleUiState.Success(transformResult)
                },
                onFailure = { error ->
                    _uiState.value = ArticleUiState.Error(error.message ?: "An unknown error occurred.")
                }
            )
        }
    }

    fun reset() {
        _uiState.value = ArticleUiState.Input
    }

    fun resetSaveStatus() {
        _saveStatus.value = null
    }

    fun saveResult() {
        val currentState = _uiState.value as? ArticleUiState.Success ?: return
        val text = _inputText.value
        val preview = if (text.length > 50) text.take(50).replace("\n", " ") + "..." else text.replace("\n", " ")
        
        val item = HistoryItem(
            workflowType = WorkflowType.ARTICLE_TRANSFORMER,
            inputPreview = preview,
            transformType = currentState.result.transformType,
            outputText = currentState.result.outputText,
            providerType = currentState.result.providerType,
            createdAtMillis = System.currentTimeMillis()
        )
        
        viewModelScope.launch {
            historyRepository.saveHistoryItem(item)
            _saveStatus.value = "Saved to history successfully."
        }
    }
}
