package com.synapse.app.feature.workflow.editor

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.synapse.app.domain.actions.WorkflowActionRegistry
import com.synapse.app.domain.models.*
import com.synapse.app.domain.readiness.WorkflowReadiness
import com.synapse.app.feature.workflow.components.ActionBlockList
import com.synapse.app.feature.workflow.components.ProfilePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowEditorScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onTestRun: (Long) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: WorkflowEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val availableNotes by viewModel.availableNotes.collectAsState()
    val availableWorkflows by viewModel.availableWorkflows.collectAsState()
    val availableProfiles by viewModel.availableProfiles.collectAsState()

    // Notification permission launcher (Android 13+)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.checkNotificationPermission()
    }

    // Refresh readiness when returning from Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshReadiness()
                viewModel.checkNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Navigate after user dismisses confirmation dialog
    LaunchedEffect(state.savedTemplateId) {
        state.savedTemplateId?.let { onSaved(it) }
    }

    // ── Save confirmation dialog ──
    state.saveConfirmation?.let { conf ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirmation() },
            icon = {
                Icon(
                    if (conf.isScheduled) Icons.Default.CheckCircle else Icons.Default.Save,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(if (conf.isScheduled) "Schedule Active" else "Workflow Saved")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "\"${conf.workflowName}\"",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (conf.isScheduled) {
                        Text(conf.scheduleMessage, style = MaterialTheme.typography.bodyMedium)
                        if (conf.nextRunLabel.isNotBlank()) {
                            Text(
                                "Next run: ${conf.nextRunLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column {
                                Text("Start alert", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (conf.notifyOnStart) "On" else "Off",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Column {
                                Text("Completion alert", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (conf.notifyOnCompletion) "On" else "Off",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        Text("Saved successfully. Run it manually anytime from My Workflows.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissConfirmation() }) {
                    Text("Done")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isEditing) "Edit Workflow"
                        else if (state.sourceTemplateName.isNotBlank()) "New from ${state.sourceTemplateName}"
                        else "New Workflow"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = {}
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Template origin — informational only ──
            if (state.sourceTemplateName.isNotBlank()) {
                AssistChip(
                    onClick = {},
                    label = { Text("Started from: ${state.sourceTemplateName}") },
                    leadingIcon = {
                        Icon(Icons.Default.Dashboard, null, modifier = Modifier.size(16.dp))
                    }
                )
            }

            // ── Readiness Bar ──
            ReadinessBar(
                readiness = state.workflowReadiness,
                onSetup = onNavigateToSettings
            )

            // ── Section 1: Basic Info ──
            SectionHeader("1", "Basic Info")

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text("Workflow Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = state.validationErrors.any { "name" in it.lowercase() }
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::updateDescription,
                label = { Text("Description (optional)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            // Workflow default profile
            if (availableProfiles.isNotEmpty()) {
                ProfilePicker(
                    label = "Default AI Profile",
                    hint = "Used for all AI steps unless overridden",
                    selectedProfileId = state.defaultProfileId,
                    profiles = availableProfiles,
                    onProfileSelected = viewModel::updateDefaultProfileId,
                    inheritLabel = "Use app default"
                )
            }

            // ── Section 2: Trigger ──
            SectionHeader("2", "Trigger")
            TriggerSection(
                trigger = state.trigger,
                onTriggerChanged = viewModel::updateTrigger
            )

            // ── Section 3: Input Actions ──
            SectionHeader("3", "Input Actions")
            Text(
                "Add data sources for your workflow to process.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ActionBlockList(
                actions = state.actions,
                onActionsChanged = viewModel::updateActions,
                availableNotes = availableNotes,
                availableWorkflows = availableWorkflows,
                availableProfiles = availableProfiles,
                readinessEvaluator = viewModel.readinessEvaluator,
                onNavigateToSettings = onNavigateToSettings
            )

            // ── Section 4: Processing Instructions ──
            SectionHeader("4", "Processing Instructions")

            OutlinedTextField(
                value = state.globalInstruction,
                onValueChange = viewModel::updateGlobalInstruction,
                label = { Text("Global Instruction") },
                placeholder = { Text("e.g. Combine all sources into a concise morning briefing") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Section 5: Output ──
            SectionHeader("5", "Output")
            OutputSection(
                config = state.outputConfig,
                onConfigChanged = viewModel::updateOutputConfig,
                profiles = availableProfiles
            )

            // ── Section 6: Notifications ──
            SectionHeader("6", "Notifications")

            // Permission banner
            if (state.needsNotificationPermission) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Notification permission needed",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "To receive alerts when your scheduled workflows start, complete, or fail, Synapse needs notification permission.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text("Allow Notifications")
                        }
                    }
                }
            }

            // Notify on start toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Notify when run starts")
                    Text(
                        "Get an alert when a scheduled run begins",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.notifyOnStart,
                    onCheckedChange = viewModel::updateNotifyOnStart
                )
            }

            // Notify on completion toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Notify on completion")
                    Text(
                        "Get an alert when a run finishes successfully",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.notifyOnCompletion,
                    onCheckedChange = viewModel::updateNotifyOnCompletion
                )
            }

            Text(
                "Failure notifications are always sent so you know when something goes wrong.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            // ── Validation Errors ──
            if (state.validationErrors.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        state.validationErrors.forEach { error ->
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // ── Save Button ──
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                val isScheduled = state.trigger !is WorkflowTrigger.Manual
                Text(
                    when {
                        state.isEditing && isScheduled -> "Update & Activate Schedule"
                        state.isEditing -> "Update Workflow"
                        isScheduled -> "Save & Activate Schedule"
                        else -> "Save Workflow"
                    }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    number,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TriggerSection(
    trigger: WorkflowTrigger,
    onTriggerChanged: (WorkflowTrigger) -> Unit
) {
    val triggerOptions = listOf("Manual", "Daily", "Weekly", "Interval", "Notification (Coming Soon)")
    val selectedIndex = when (trigger) {
        is WorkflowTrigger.Manual -> 0
        is WorkflowTrigger.Daily -> 1
        is WorkflowTrigger.Weekly -> 2
        is WorkflowTrigger.Interval -> 3
        is WorkflowTrigger.NotificationKeyword -> 4
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        triggerOptions.forEachIndexed { index, label ->
            val isNotification = index == 4
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(
                    selected = selectedIndex == index,
                    onClick = {
                        if (!isNotification) {
                            when (index) {
                                0 -> onTriggerChanged(WorkflowTrigger.Manual)
                                1 -> onTriggerChanged(WorkflowTrigger.Daily())
                                2 -> onTriggerChanged(WorkflowTrigger.Weekly())
                                3 -> onTriggerChanged(WorkflowTrigger.Interval())
                            }
                        }
                    },
                    enabled = !isNotification
                )
                Text(
                    label,
                    color = if (isNotification) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Time picker for Daily/Weekly
        if (trigger is WorkflowTrigger.Daily || trigger is WorkflowTrigger.Weekly) {
            val currentHour = when (trigger) {
                is WorkflowTrigger.Daily -> trigger.hour
                is WorkflowTrigger.Weekly -> trigger.hour
                else -> 8
            }
            val currentMinute = when (trigger) {
                is WorkflowTrigger.Daily -> trigger.minute
                is WorkflowTrigger.Weekly -> trigger.minute
                else -> 0
            }

            TimePickerButton(
                hour = currentHour,
                minute = currentMinute,
                onTimeSelected = { h, m ->
                    when (trigger) {
                        is WorkflowTrigger.Daily -> onTriggerChanged(trigger.copy(hour = h, minute = m))
                        is WorkflowTrigger.Weekly -> onTriggerChanged(trigger.copy(hour = h, minute = m))
                        else -> {}
                    }
                },
                modifier = Modifier.padding(start = 48.dp)
            )
        }

        // Day selector for Weekly
        if (trigger is WorkflowTrigger.Weekly) {
            val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            Column(modifier = Modifier.padding(start = 48.dp)) {
                Text("Days:", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayNames.forEachIndexed { i, name ->
                        val dayNum = i + 1
                        FilterChip(
                            selected = dayNum in trigger.daysOfWeek,
                            onClick = {
                                val newDays = if (dayNum in trigger.daysOfWeek)
                                    trigger.daysOfWeek - dayNum else trigger.daysOfWeek + dayNum
                                onTriggerChanged(trigger.copy(daysOfWeek = newDays))
                            },
                            label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        // Interval presets
        if (trigger is WorkflowTrigger.Interval) {
            val presets = listOf(15, 30, 60, 120, 240, 480)
            Column(modifier = Modifier.padding(start = 48.dp)) {
                Text("Run every:", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presets.forEach { mins ->
                        val label = when {
                            mins < 60 -> "${mins}m"
                            mins == 60 -> "1h"
                            else -> "${mins / 60}h"
                        }
                        FilterChip(
                            selected = trigger.intervalMinutes == mins,
                            onClick = { onTriggerChanged(trigger.copy(intervalMinutes = mins)) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        // Show computed next run
        if (trigger is WorkflowTrigger.Daily || trigger is WorkflowTrigger.Weekly || trigger is WorkflowTrigger.Interval) {
            val nextRun = computeNextRunPreview(trigger)
            Text(
                "Next run: $nextRun",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 48.dp, top = 4.dp)
            )
            val timingNote = if (trigger is WorkflowTrigger.Interval) {
                "Runs again after each interval, not at fixed clock times. Android may adjust timing by a few minutes."
            } else {
                "Android may adjust the actual run time by a few minutes to save battery."
            }
            Text(
                timingNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 48.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerButton(
    hour: Int,
    minute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { showPicker = true }, modifier = modifier) {
        Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Run at ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }

    if (showPicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Select Time") },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                Button(onClick = {
                    onTimeSelected(timePickerState.hour, timePickerState.minute)
                    showPicker = false
                }) { Text("Set Time") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        )
    }
}

private fun computeNextRunPreview(trigger: WorkflowTrigger): String {
    val dateFormat = java.text.SimpleDateFormat("EEE, MMM d 'at' HH:mm", java.util.Locale.getDefault())
    val now = java.util.Calendar.getInstance()

    return when (trigger) {
        is WorkflowTrigger.Daily -> {
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, trigger.hour)
                set(java.util.Calendar.MINUTE, trigger.minute)
                set(java.util.Calendar.SECOND, 0)
            }
            if (target.timeInMillis <= now.timeInMillis) target.add(java.util.Calendar.DAY_OF_YEAR, 1)
            dateFormat.format(target.time)
        }
        is WorkflowTrigger.Weekly -> {
            val todayIso = when (now.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.MONDAY -> 1; java.util.Calendar.TUESDAY -> 2
                java.util.Calendar.WEDNESDAY -> 3; java.util.Calendar.THURSDAY -> 4
                java.util.Calendar.FRIDAY -> 5; java.util.Calendar.SATURDAY -> 6
                java.util.Calendar.SUNDAY -> 7; else -> 1
            }
            var bestTarget: java.util.Calendar? = null
            for (day in trigger.daysOfWeek) {
                var daysAhead = day - todayIso
                if (daysAhead < 0) daysAhead += 7
                val t = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_YEAR, daysAhead)
                    set(java.util.Calendar.HOUR_OF_DAY, trigger.hour)
                    set(java.util.Calendar.MINUTE, trigger.minute)
                    set(java.util.Calendar.SECOND, 0)
                }
                if (t.timeInMillis <= now.timeInMillis) t.add(java.util.Calendar.DAY_OF_YEAR, 7)
                if (bestTarget == null || t.timeInMillis < bestTarget.timeInMillis) bestTarget = t
            }
            bestTarget?.let { dateFormat.format(it.time) } ?: "Unknown"
        }
        is WorkflowTrigger.Interval -> {
            val target = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.MINUTE, trigger.intervalMinutes)
            }
            dateFormat.format(target.time)
        }
        else -> "Not scheduled"
    }
}

@Composable
private fun OutputSection(
    config: WorkflowOutputConfig,
    onConfigChanged: (WorkflowOutputConfig) -> Unit,
    profiles: List<ProviderProfile> = emptyList()
) {
    val isSocialMode = config.outputType == WorkflowOutputType.SOCIAL_POST ||
            config.outputType == WorkflowOutputType.BOTH

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Output type
        Text("Output Type", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        WorkflowOutputType.entries.forEach { type ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(
                    selected = config.outputType == type,
                    onClick = { onConfigChanged(config.copy(outputType = type)) }
                )
                Text(type.displayName)
            }
        }

        // ── Social Platform Selection (shown when Social Post or Both) ──
        if (isSocialMode) {
            Spacer(Modifier.height(4.dp))
            SocialPlatformSection(config = config, onConfigChanged = onConfigChanged)
        }

        // Output format (hidden for social mode — uses structured JSON internally)
        if (!isSocialMode) {
            Spacer(Modifier.height(4.dp))
            Text("Output Format", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Text(
                "Controls how the AI structures its response and how the result is displayed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            OutputFormat.entries.forEach { format ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = config.outputFormat == format,
                        onClick = { onConfigChanged(config.copy(outputFormat = format)) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(format.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            format.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Info note for social mode
            Spacer(Modifier.height(4.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Social output uses structured generation internally. Results display as per-platform cards with individual copy and share actions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Output profile override
        if (profiles.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            ProfilePicker(
                label = "Output AI Profile",
                hint = "Override the AI profile for final output generation",
                selectedProfileId = config.outputProfileId,
                profiles = profiles,
                onProfileSelected = { onConfigChanged(config.copy(outputProfileId = it)) },
                inheritLabel = "Use workflow default"
            )
        }
    }
}

// ── Social Platform Selection UI ──

@Composable
private fun SocialPlatformSection(
    config: WorkflowOutputConfig,
    onConfigChanged: (WorkflowOutputConfig) -> Unit
) {
    var showAddCustom by remember { mutableStateOf(false) }
    var customPlatformName by remember { mutableStateOf("") }
    var expandedPlatform by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Target Platforms", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        Text(
            "Select platforms to generate tailored content for. Each gets its own output card.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Built-in platforms
        SocialPlatform.entries.forEach { platform ->
            val isSelected = config.socialPlatforms.contains(platform)
            val platformName = platform.displayName
            val instruction = config.platformInstructions[platformName] ?: ""

            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { checked ->
                            val updated = if (checked) config.socialPlatforms + platform
                            else config.socialPlatforms - platform
                            onConfigChanged(config.copy(socialPlatforms = updated))
                        }
                    )
                    Text(platformName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (isSelected) {
                        IconButton(
                            onClick = { expandedPlatform = if (expandedPlatform == platformName) null else platformName },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (expandedPlatform == platformName) Icons.Default.ExpandLess else Icons.Default.Tune,
                                "Instructions",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                // Per-platform instruction
                if (isSelected && expandedPlatform == platformName) {
                    OutlinedTextField(
                        value = instruction,
                        onValueChange = { newInst ->
                            val updated = config.platformInstructions.toMutableMap()
                            if (newInst.isBlank()) updated.remove(platformName)
                            else updated[platformName] = newInst
                            onConfigChanged(config.copy(platformInstructions = updated))
                        },
                        label = { Text("$platformName style instructions") },
                        placeholder = { Text("e.g. Keep it under 280 chars, use hashtags...") },
                        modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
                        minLines = 2,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Custom platforms
        config.customPlatforms.forEachIndexed { index, customName ->
            val instruction = config.platformInstructions[customName] ?: ""

            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = true, onCheckedChange = null, enabled = false) // always checked — remove to deselect
                    Text(customName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { expandedPlatform = if (expandedPlatform == customName) null else customName },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (expandedPlatform == customName) Icons.Default.ExpandLess else Icons.Default.Tune,
                            "Instructions",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            val updatedCustom = config.customPlatforms.toMutableList().apply { removeAt(index) }
                            val updatedInstructions = config.platformInstructions.toMutableMap().apply { remove(customName) }
                            onConfigChanged(config.copy(customPlatforms = updatedCustom, platformInstructions = updatedInstructions))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(18.dp))
                    }
                }
                if (expandedPlatform == customName) {
                    OutlinedTextField(
                        value = instruction,
                        onValueChange = { newInst ->
                            val updated = config.platformInstructions.toMutableMap()
                            if (newInst.isBlank()) updated.remove(customName)
                            else updated[customName] = newInst
                            onConfigChanged(config.copy(platformInstructions = updated))
                        },
                        label = { Text("$customName style instructions") },
                        placeholder = { Text("Describe the tone and style for this platform...") },
                        modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
                        minLines = 2,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Add custom platform
        if (showAddCustom) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 40.dp)
            ) {
                OutlinedTextField(
                    value = customPlatformName,
                    onValueChange = { customPlatformName = it },
                    label = { Text("Platform name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    if (customPlatformName.isNotBlank()) {
                        val updatedCustom = config.customPlatforms + customPlatformName.trim()
                        onConfigChanged(config.copy(customPlatforms = updatedCustom))
                        customPlatformName = ""
                        showAddCustom = false
                    }
                }) {
                    Icon(Icons.Default.Check, "Add")
                }
                IconButton(onClick = { showAddCustom = false; customPlatformName = "" }) {
                    Icon(Icons.Default.Close, "Cancel")
                }
            }
        } else {
            TextButton(onClick = { showAddCustom = true }) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Custom Platform")
            }
        }

        // Global social instruction
        Spacer(Modifier.height(4.dp))
        Text("Global Social Instruction", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        Text(
            "Shared instruction applied to all platform outputs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = config.socialGlobalInstruction,
            onValueChange = { onConfigChanged(config.copy(socialGlobalInstruction = it)) },
            placeholder = { Text("e.g. Focus on AI and tech trends. Keep a professional but approachable tone.") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            textStyle = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ReadinessBar(
    readiness: WorkflowReadiness?,
    onSetup: () -> Unit
) {
    if (readiness == null) return

    val containerColor = if (readiness.isFullyReady)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)

    val contentColor = if (readiness.isFullyReady)
        MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onErrorContainer

    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (readiness.isFullyReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (readiness.isFullyReady) {
                    Text("Ready to run", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = contentColor)
                } else {
                    val setupCount = readiness.needsSetupActions.size
                    Text(
                        "$setupCount action(s) need setup",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = contentColor
                    )
                    val names = readiness.needsSetupActions.joinToString(", ") {
                        WorkflowActionRegistry.getInfo(it.type).displayName
                    }
                    Text(names, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.8f))
                }
            }
            if (!readiness.isFullyReady) {
                TextButton(onClick = onSetup) {
                    Text("Fix in Settings", color = contentColor)
                }
            }
        }
    }
}
