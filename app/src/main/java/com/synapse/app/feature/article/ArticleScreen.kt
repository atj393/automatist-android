package com.synapse.app.feature.article

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.synapse.app.domain.models.TransformType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    sharedText: String,
    onBack: () -> Unit,
    viewModel: ArticleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val selectedTransform by viewModel.selectedTransform.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()
    val context = LocalContext.current
    
    LaunchedEffect(sharedText) {
        viewModel.setInitialTextOnce(sharedText)
    }

    LaunchedEffect(saveStatus) {
        saveStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.resetSaveStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Article Transformer") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState is ArticleUiState.Success || uiState is ArticleUiState.Error) {
                            viewModel.reset()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (val state = uiState) {
                is ArticleUiState.Input -> {
                    ArticleInputContent(
                        inputText = inputText,
                        onInputChanged = viewModel::updateInputText,
                        selectedTransform = selectedTransform,
                        onTransformSelected = viewModel::selectTransform,
                        onTransformClick = viewModel::transform
                    )
                }
                is ArticleUiState.Loading -> {
                    LoadingContent()
                }
                is ArticleUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = viewModel::reset
                    )
                }
                is ArticleUiState.Success -> {
                    ResultContent(
                        resultText = state.result.outputText,
                        onCopy = { copyToClipboard(it, context) },
                        onShare = { shareText(it, context) },
                        onSave = viewModel::saveResult
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleInputContent(
    inputText: String,
    onInputChanged: (String) -> Unit,
    selectedTransform: TransformType,
    onTransformSelected: (TransformType) -> Unit,
    onTransformClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChanged,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = { Text("Paste article or notes here") },
            placeholder = { Text("Enter the text you want to transform...") }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Select Output Format", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        Column(modifier = Modifier.fillMaxWidth()) {
            listOf(TransformType.SUMMARY, TransformType.THREAD, TransformType.PRO_POST).forEach { type ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = selectedTransform == type,
                        onClick = { onTransformSelected(type) }
                    )
                    Text(text = type.displayName)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onTransformClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Transform")
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text("Processing using Local Fake Demo...")
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Go Back")
        }
    }
}

@Composable
private fun ResultContent(
    resultText: String,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = resultText,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = { onCopy(resultText) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                Spacer(Modifier.width(8.dp))
                Text("Copy")
            }
            
            OutlinedButton(onClick = { onShare(resultText) }) {
                Icon(Icons.Default.Share, contentDescription = "Share")
                Spacer(Modifier.width(8.dp))
                Text("Share")
            }
            
            Button(onClick = onSave) {
                Icon(Icons.Default.Save, contentDescription = "Save")
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
        }
    }
}

private fun copyToClipboard(text: String, context: Context) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Synapse transform result", text)
    clipboardManager.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun shareText(text: String, context: Context) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, null)
    context.startActivity(shareIntent)
}
