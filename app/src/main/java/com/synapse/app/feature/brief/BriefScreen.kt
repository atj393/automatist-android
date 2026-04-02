package com.synapse.app.feature.brief

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
import com.synapse.app.domain.models.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BriefScreen(
    onBack: () -> Unit,
    viewModel: BriefViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsState()
    val recentRuns by viewModel.recentRuns.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Morning Brief Setup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::runNow) {
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
            }

            if (selectedTabIndex == 0) {
                ConfigLayout(config, viewModel::updateConfig)
            } else {
                RecentRunsLayout(recentRuns)
            }
        }
    }
}

    @Composable
private fun ConfigLayout(config: BriefConfig, onSaveConfig: (BriefConfig) -> Unit) {
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
            Text("Notify me when draft is compiled")
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
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Automation Job")
        }
    }
}

@Composable
private fun RecentRunsLayout(runs: List<HistoryItem>) {
    if (runs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No recent automated runs. Hit the 'Run Now' play icon in the top right toolbar to process a feed manually.")
        }
        return
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(runs) { item ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Synthesizer Output", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(item.outputText.take(200).replace("\n", " ") + "...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
