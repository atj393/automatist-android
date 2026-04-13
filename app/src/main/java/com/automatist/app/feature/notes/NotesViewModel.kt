package com.automatist.app.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatist.app.domain.models.SavedNote
import com.automatist.app.domain.repositories.WorkflowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotesUiState(
    val notes: List<SavedNote> = emptyList(),
    val searchQuery: String = "",
    val editingNote: SavedNote? = null,
    val isEditorOpen: Boolean = false,
    val editorTitle: String = "",
    val editorContent: String = "",
    val isSaving: Boolean = false,
    val validationError: String? = null
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: WorkflowRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NotesUiState())
    val state = _state.asStateFlow()

    private val allNotes = repository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            allNotes.collect { notes ->
                _state.update { current ->
                    val filtered = if (current.searchQuery.isBlank()) notes
                    else notes.filter { note ->
                        note.title.contains(current.searchQuery, ignoreCase = true) ||
                        note.content.contains(current.searchQuery, ignoreCase = true)
                    }
                    current.copy(notes = filtered)
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            val all = allNotes.value
            val filtered = if (query.isBlank()) all
            else all.filter { note ->
                note.title.contains(query, ignoreCase = true) ||
                note.content.contains(query, ignoreCase = true)
            }
            _state.update { it.copy(notes = filtered) }
        }
    }

    fun openNewNote() {
        _state.update {
            it.copy(
                isEditorOpen = true,
                editingNote = null,
                editorTitle = "",
                editorContent = "",
                validationError = null
            )
        }
    }

    fun openEditNote(note: SavedNote) {
        _state.update {
            it.copy(
                isEditorOpen = true,
                editingNote = note,
                editorTitle = note.title,
                editorContent = note.content,
                validationError = null
            )
        }
    }

    fun closeEditor() {
        _state.update { it.copy(isEditorOpen = false, editingNote = null, validationError = null) }
    }

    fun updateEditorTitle(title: String) = _state.update { it.copy(editorTitle = title) }
    fun updateEditorContent(content: String) = _state.update { it.copy(editorContent = content) }

    fun saveNote() {
        val current = _state.value
        if (current.editorTitle.isBlank()) {
            _state.update { it.copy(validationError = "Title is required.") }
            return
        }
        if (current.editorContent.isBlank()) {
            _state.update { it.copy(validationError = "Content is required.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, validationError = null) }
            val now = System.currentTimeMillis()

            val existing = current.editingNote
            if (existing != null) {
                repository.updateNote(
                    existing.copy(
                        title = current.editorTitle.trim(),
                        content = current.editorContent.trim(),
                        updatedAtMillis = now
                    )
                )
            } else {
                repository.saveNote(
                    SavedNote(
                        title = current.editorTitle.trim(),
                        content = current.editorContent.trim(),
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                )
            }

            _state.update { it.copy(isSaving = false, isEditorOpen = false, editingNote = null) }
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
        }
    }
}
