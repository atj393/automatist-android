package com.synapse.app.feature.brief

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

sealed interface BriefUiState {
    data object Input : BriefUiState
    data object Loading : BriefUiState
    data class Error(val message: String) : BriefUiState
    data class Success(val result: TransformResult) : BriefUiState
}

@HiltViewModel
class BriefViewModel @Inject constructor(
    private val transformProvider: ArticleTransformProvider,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BriefUiState>(BriefUiState.Input)
    val uiState: StateFlow<BriefUiState> = _uiState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _saveStatus = MutableStateFlow<String?>(null)
    val saveStatus: StateFlow<String?> = _saveStatus.asStateFlow()

    fun updateInputText(text: String) {
        _inputText.value = text
        if (_uiState.value is BriefUiState.Error) {
            _uiState.value = BriefUiState.Input
        }
    }

    fun transform() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        _uiState.value = BriefUiState.Loading

        viewModelScope.launch {
            val result = transformProvider.transform(
                input = ArticleInput(text = text),
                type = TransformType.MORNING_SUMMARY
            )

            result.fold(
                onSuccess = { _uiState.value = BriefUiState.Success(it) },
                onFailure = { _uiState.value = BriefUiState.Error(it.message ?: "Unknown error") }
            )
        }
    }

    fun reset() {
        _uiState.value = BriefUiState.Input
    }

    fun resetSaveStatus() {
        _saveStatus.value = null
    }

    fun saveResult() {
        val state = _uiState.value as? BriefUiState.Success ?: return
        val preview = _inputText.value.take(50).replace("\n", " ") + "..."

        viewModelScope.launch {
            historyRepository.saveHistoryItem(
                HistoryItem(
                    workflowType = WorkflowType.MORNING_BRIEF,
                    inputPreview = preview,
                    transformType = state.result.transformType,
                    outputText = state.result.outputText,
                    providerType = state.result.providerType,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
            _saveStatus.value = "Saved successfully."
        }
    }
}
