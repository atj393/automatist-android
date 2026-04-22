package com.automatist.app.feature.brief

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.WorkInfo
import com.automatist.app.domain.models.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BriefScreen(
    onBack: () -> Unit,
    viewModel: BriefViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsState()
    val recentRuns by viewModel.recentRuns.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }

    val context = LocalContext.current
    val workState by viewModel.workState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Morning Brief") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.runNow()
                        selectedTabIndex = 2
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run Now")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Configuration") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Recent Runs") }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("Live Progress") }
                )
            }

            when (selectedTabIndex) {
                0 -> ConfigLayout(config, viewModel::updateConfig)
                1 -> RecentRunsLayout(recentRuns)
                2 -> OngoingProcessLayout(workState)
            }
        }
    }
}

@Composable
private fun ConfigLayout(config: BriefConfig, onSaveConfig: (BriefConfig) -> Unit) {
    val context = LocalContext.current
    var rawFeeds by remember(config.rssFeeds) { mutableStateOf(config.rssFeeds.joinToString("\n")) }
    var outputType by remember(config.briefOutputType) { mutableStateOf(config.briefOutputType) }
    var scheduleType by remember(config.scheduleType) { mutableStateOf(config.scheduleType) }
    var interval by remember(config.intervalHours) { mutableStateOf(config.intervalHours?.toString() ?: "4") }
    var notify by remember(config.isNotificationsEnabled) { mutableStateOf(config.isNotificationsEnabled) }
    
    // Manage multi-select
    val selectedPlatforms = remember(config.socialPlatforms) { mutableStateListOf(*config.socialPlatforms.toTypedArray()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("1. Schedule Trigger", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = scheduleType == ScheduleType.EVERY_N_HOURS, onClick = { scheduleType = ScheduleType.EVERY_N_HOURS })
            Text("Interval")
            Spacer(Modifier.width(16.dp))
            RadioButton(selected = scheduleType == ScheduleType.DAILY_AT_HOUR, onClick = { scheduleType = ScheduleType.DAILY_AT_HOUR })
            Text("Daily")
        }
        if (scheduleType == ScheduleType.EVERY_N_HOURS) {
            OutlinedTextField(
                value = interval,
                onValueChange = { interval = it },
                label = { Text("Hours Interval (e.g. 4)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))

        Text("2. RSS Feeds (One per line)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = rawFeeds,
            onValueChange = { rawFeeds = it },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            placeholder = { Text("https://techcrunch.com/feed/") }
        )
        Spacer(Modifier.height(16.dp))

        Text("3. Output Formats", style = MaterialTheme.typography.titleMedium)
        BriefOutputType.values().forEach { type ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = outputType == type, onClick = { outputType = type })
                Text(type.displayName)
            }
        }
        Spacer(Modifier.height(16.dp))

        if (outputType == BriefOutputType.SOCIAL_POST) {
            Text("4. Social Targets (Multi-Select)", style = MaterialTheme.typography.titleMedium)
            SocialPlatform.values().forEach { platform ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = selectedPlatforms.contains(platform),
                        onCheckedChange = { isChecked ->
                            if (isChecked) selectedPlatforms.add(platform) else selectedPlatforms.remove(platform)
                        }
                    )
                    Text(platform.displayName)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Text("5. Alerts", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = notify, onCheckedChange = { notify = it })
            Text("Notify me when the brief is ready")
        }
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                val updatedConfig = config.copy(
                    scheduleType = scheduleType,
                    intervalHours = interval.toIntOrNull() ?: 4,
                    rssFeeds = rawFeeds.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                    briefOutputType = outputType,
                    socialPlatforms = selectedPlatforms.toSet(),
                    isNotificationsEnabled = notify
                )
                onSaveConfig(updatedConfig)
                Toast.makeText(context, "Configuration saved", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Schedule")
        }
    }
}

@Composable
private fun RecentRunsLayout(runs: List<HistoryItem>) {
    if (runs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No runs yet. Tap the play button above to run the brief manually.")
        }
        return
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(runs) { item ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Generated Brief", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(item.outputText.take(200).replace("\n", " ") + "...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun OngoingProcessLayout(workState: WorkInfo?) {
    if (workState == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing is running. Tap Run Now to start.")
        }
        return
    }

    val status = workState.progress.getString("status") ?: "Starting..."
    val error = workState.progress.getString("error")
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (error != null || workState.state == WorkInfo.State.FAILED) {
            Text(error ?: "Something went wrong.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
        } else if (workState.state == WorkInfo.State.SUCCEEDED) {
            Text("Brief generated!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        } else {
            CircularProgressIndicator()
            Spacer(Modifier.height(24.dp))
            Text(status, style = MaterialTheme.typography.titleMedium)
        }
    }
}
