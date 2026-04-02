package com.synapse.app.feature.meeting

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

sealed interface MeetingUiState {
    data object Input : MeetingUiState
    data object Loading : MeetingUiState
    data class Error(val message: String) : MeetingUiState
    data class Success(val result: TransformResult) : MeetingUiState
}

@HiltViewModel
class MeetingViewModel @Inject constructor(
    private val transformProvider: ArticleTransformProvider,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MeetingUiState>(MeetingUiState.Input)
    val uiState: StateFlow<MeetingUiState> = _uiState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _selectedTransform = MutableStateFlow(TransformType.MEETING_BRIEF)
    val selectedTransform: StateFlow<TransformType> = _selectedTransform.asStateFlow()

    private val _saveStatus = MutableStateFlow<String?>(null)
    val saveStatus: StateFlow<String?> = _saveStatus.asStateFlow()

    fun updateInputText(text: String) {
        _inputText.value = text
        if (_uiState.value is MeetingUiState.Error) {
            _uiState.value = MeetingUiState.Input
        }
    }

    fun selectTransform(type: TransformType) {
        _selectedTransform.value = type
    }

    fun transform() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        _uiState.value = MeetingUiState.Loading

        viewModelScope.launch {
            val result = transformProvider.transform(
                input = ArticleInput(text = text),
                type = _selectedTransform.value
            )

            result.fold(
                onSuccess = { _uiState.value = MeetingUiState.Success(it) },
                onFailure = { _uiState.value = MeetingUiState.Error(it.message ?: "Unknown error") }
            )
        }
    }

    fun reset() {
        _uiState.value = MeetingUiState.Input
    }

    fun resetSaveStatus() {
        _saveStatus.value = null
    }

    fun saveResult() {
        val state = _uiState.value as? MeetingUiState.Success ?: return
        val preview = _inputText.value.take(50).replace("\n", " ") + "..."

        viewModelScope.launch {
            historyRepository.saveHistoryItem(
                HistoryItem(
                    workflowType = WorkflowType.MEETING_STRATEGIST,
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
