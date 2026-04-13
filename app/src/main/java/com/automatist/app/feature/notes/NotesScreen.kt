package com.automatist.app.feature.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.automatist.app.domain.models.SavedNote
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onBack: () -> Unit,
    viewModel: NotesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Notes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!state.isEditorOpen) {
                FloatingActionButton(onClick = viewModel::openNewNote) {
                    Icon(Icons.Default.Add, "New Note")
                }
            }
        }
    ) { padding ->
        if (state.isEditorOpen) {
            NoteEditor(
                title = state.editorTitle,
                content = state.editorContent,
                isEditing = state.editingNote != null,
                isSaving = state.isSaving,
                validationError = state.validationError,
                onTitleChange = viewModel::updateEditorTitle,
                onContentChange = viewModel::updateEditorContent,
                onSave = viewModel::saveNote,
                onCancel = viewModel::closeEditor,
                modifier = Modifier.padding(padding)
            )
        } else {
            NotesList(
                notes = state.notes,
                searchQuery = state.searchQuery,
                onSearchChange = viewModel::updateSearchQuery,
                onNoteClick = viewModel::openEditNote,
                onDeleteNote = viewModel::deleteNote,
                onCreate = viewModel::openNewNote,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun NotesList(
    notes: List<SavedNote>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onNoteClick: (SavedNote) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search notes...") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, "Clear", modifier = Modifier.size(20.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (notes.isEmpty()) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.StickyNote2, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (searchQuery.isBlank()) "No saved notes yet"
                        else "No notes match your search",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Notes are reusable content blocks for your workflows",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    if (searchQuery.isBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onCreate) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Create Note")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(note = note, onClick = { onNoteClick(note) }, onDelete = { onDeleteNote(note.id) })
                }
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: SavedNote,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        note.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        note.content.take(100).replace("\n", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Updated ${dateFormat.format(Date(note.updatedAtMillis))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Note") },
            text = {
                Text("Delete \"${note.title}\"? Workflows referencing this note will fall back to any inline content provided.")
            },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NoteEditor(
    title: String,
    content: String,
    isEditing: Boolean,
    isSaving: Boolean,
    validationError: String?,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (isEditing) "Edit Note" else "New Note",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = validationError != null && "title" in validationError.lowercase()
        )

        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            label = { Text("Content *") },
            minLines = 8,
            maxLines = 20,
            modifier = Modifier.fillMaxWidth().weight(1f),
            isError = validationError != null && "content" in validationError.lowercase()
        )

        if (validationError != null) {
            Text(validationError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = !isSaving) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isEditing) "Update" else "Save")
            }
        }
    }
}
