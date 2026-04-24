package com.automatist.app.feature.workflow.editor

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.automatist.app.feature.onboarding.canRequestNotificationPermission
import com.automatist.app.feature.onboarding.openAppNotificationSettings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.automatist.app.domain.actions.WorkflowActionRegistry
import com.automatist.app.domain.models.*
import com.automatist.app.domain.readiness.WorkflowReadiness
import com.automatist.app.feature.vault.SettingsSection
import com.automatist.app.feature.workflow.components.ActionBlockList
import com.automatist.app.feature.workflow.components.ProfilePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowEditorScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onTestRun: (Long) -> Unit,
    onNavigateToSettings: (String) -> Unit = {},
    viewModel: WorkflowEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val availableNotes by viewModel.availableNotes.collectAsState()
    val availableWorkflows by viewModel.availableWorkflows.collectAsState()
    val availableProfiles by viewModel.availableProfiles.collectAsState()

    // Notification permission launcher (Android 13+). We track attempts locally so we
    // can honestly tell the user when the OS won't show the dialog anymore and they
    // need to go to system settings.
    val context = LocalContext.current
    val activity = context as? Activity
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        permissionAsked = true
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
                // Let the catalog decide which Settings section to open — AI
                // Profiles for AI Prompt, Service API Keys for weather/routes,
                // etc. Prior version hardcoded SERVICE_KEYS for every action,
                // which is why AI Prompt's "Set Up" landed on the wrong page.
                onNavigateToSettings = { section -> onNavigateToSettings(section) }
            )

            // ── Section 4: Final Output Prompt ──
            // Renamed from "Processing Instructions" / "Global Instruction":
            // users repeatedly missed that this is the single instruction that
            // runs AFTER all actions complete, on the combined outputs.
            // Storage contract is unchanged — the field still writes into
            // `WorkflowTemplate.globalInstruction`, which the engine still
            // appends as the "Extra: …" line to the final system prompt.
            SectionHeader("4", "Final Output Prompt")

            // Short, non-technical explanation. Clarifies the two-tier
            // instruction model without exposing internal engine details.
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "How this prompt is used",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Each action above can have its own per-step instruction that affects only that action. " +
                            "After all actions finish, their outputs are combined and this Final Output Prompt " +
                            "is applied once to produce the final result. Output type and platform settings in " +
                            "Section 5 below add formatting guidance automatically — you don't need to repeat them here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = state.globalInstruction,
                onValueChange = viewModel::updateGlobalInstruction,
                label = { Text("Final instruction for the combined output") },
                placeholder = {
                    Text("e.g. Combine all sources into a concise morning briefing with clear section headings.")
                },
                supportingText = {
                    Text(
                        "Runs once at the end, on the combined outputs of all actions above. Optional — leave " +
                            "blank to let Output settings drive the result.",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
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

            // Permission banner. When the OS will still show the runtime dialog, we
            // use the in-app launcher. When it won't (permanently denied), we route the
            // user to the system app-notification-settings screen so the state of the
            // toggles below isn't silently broken.
            if (state.needsNotificationPermission) {
                val canRequest = activity?.let {
                    canRequestNotificationPermission(it, permissionAsked)
                } ?: false

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            if (canRequest) "Notification permission needed"
                            else "Notifications blocked in system settings",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (canRequest)
                                "To receive alerts when your scheduled workflows start, complete, or fail, Automatist needs notification permission."
                            else
                                "Notifications are disabled for Automatist at the system level. The toggles below will save, but nothing will be delivered until you re-enable notifications in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (canRequest && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    activity?.let { openAppNotificationSettings(it) }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text(if (canRequest) "Allow Notifications" else "Open Settings")
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

            // ── Auto-retry toggle ──
            // Execution-behaviour setting rather than a notification, but slotted
            // here because it's the only other per-workflow run-behaviour control
            // and adding a new section just for one switch would be over-engineering.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Retry automatically")
                    Text(
                        "If enabled, failed runs will retry up to 3 times automatically. " +
                            "Successful earlier stages are reused when safe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.autoRetryEnabled,
                    onCheckedChange = viewModel::updateAutoRetryEnabled
                )
            }

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

        // Flexible Minutes (1–59) / Hours (1–24) picker backed by the same
        // intervalMinutes integer that the scheduler already consumes.
        // Storage contract is preserved byte-for-byte.
        if (trigger is WorkflowTrigger.Interval) {
            IntervalPicker(
                intervalMinutes = trigger.intervalMinutes,
                onChange = { newMinutes -> onTriggerChanged(trigger.copy(intervalMinutes = newMinutes)) },
                modifier = Modifier.padding(start = 48.dp)
            )
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

/**
 * Derive the UI's (value, unit) pair from a stored `intervalMinutes` integer.
 *
 * Storage remains the single source of truth — this function is pure and
 * lossless for all values that fit the new picker (Minutes 1–59, Hours 1–24).
 * Values that don't fit cleanly (legacy `90`, imported `150`, absurd `10000`)
 * are SNAPPED for display so the picker always renders a valid state, but
 * the caller keeps the raw stored value until the user actually changes
 * something in the picker. This snapping is display-only; it never mutates
 * the stored interval until the user explicitly commits a change.
 */
internal fun deriveIntervalUi(intervalMinutes: Int): IntervalUi {
    val m = intervalMinutes
    return when {
        m in 1..59 -> IntervalUi(m, IntervalUnit.Minutes, snapped = false)
        m == 0 || m < 0 -> IntervalUi(15, IntervalUnit.Minutes, snapped = true)
        m % 60 == 0 && m / 60 in 1..24 -> IntervalUi(m / 60, IntervalUnit.Hours, snapped = false)
        m in 60..(24 * 60) -> IntervalUi((m / 60).coerceIn(1, 24), IntervalUnit.Hours, snapped = true)
        else -> IntervalUi(24, IntervalUnit.Hours, snapped = true)
    }
}

internal enum class IntervalUnit { Minutes, Hours }

internal data class IntervalUi(
    val value: Int,
    val unit: IntervalUnit,
    /**
     * True when the stored [WorkflowTrigger.Interval.intervalMinutes] didn't
     * fit the new picker ranges cleanly and the displayed (value, unit) was
     * rounded. Lets the UI surface a small note so the user understands why
     * the displayed value doesn't exactly match what was previously saved.
     */
    val snapped: Boolean
)

/** Convert a picker selection back to the storage integer. */
internal fun intervalUiToMinutes(value: Int, unit: IntervalUnit): Int = when (unit) {
    IntervalUnit.Minutes -> value.coerceIn(1, 59)
    IntervalUnit.Hours -> value.coerceIn(1, 24) * 60
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalPicker(
    intervalMinutes: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val ui = remember(intervalMinutes) { deriveIntervalUi(intervalMinutes) }
    val maxValue = when (ui.unit) {
        IntervalUnit.Minutes -> 59
        IntervalUnit.Hours -> 24
    }
    val minValue = 1

    Column(modifier = modifier) {
        Text("Run every:", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Stepper — compact, mobile-friendly, no keyboard required. Caps
            // at the unit's maximum so the user can't overshoot into an
            // invalid state. Each tap commits immediately.
            IconButton(
                onClick = {
                    val next = (ui.value - 1).coerceIn(minValue, maxValue)
                    if (next != ui.value) onChange(intervalUiToMinutes(next, ui.unit))
                },
                enabled = ui.value > minValue,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Remove, "Decrease", modifier = Modifier.size(18.dp))
            }
            Text(
                ui.value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.widthIn(min = 28.dp)
            )
            IconButton(
                onClick = {
                    val next = (ui.value + 1).coerceIn(minValue, maxValue)
                    if (next != ui.value) onChange(intervalUiToMinutes(next, ui.unit))
                },
                enabled = ui.value < maxValue,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Add, "Increase", modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            // Unit selector — SingleChoiceSegmentedButtonRow is the cleanest
            // Material pattern for a two-option toggle and handles accessibility
            // (role, semantics) automatically.
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = ui.unit == IntervalUnit.Minutes,
                    onClick = {
                        if (ui.unit != IntervalUnit.Minutes) {
                            val clamped = ui.value.coerceIn(1, 59)
                            onChange(intervalUiToMinutes(clamped, IntervalUnit.Minutes))
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Minutes", style = MaterialTheme.typography.labelSmall) }
                SegmentedButton(
                    selected = ui.unit == IntervalUnit.Hours,
                    onClick = {
                        if (ui.unit != IntervalUnit.Hours) {
                            val clamped = ui.value.coerceIn(1, 24)
                            onChange(intervalUiToMinutes(clamped, IntervalUnit.Hours))
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Hours", style = MaterialTheme.typography.labelSmall) }
            }
        }

        // Natural-language summary with correct pluralisation, and a small
        // footnote when the stored value had to be snapped for display.
        Spacer(Modifier.height(4.dp))
        Text(
            "Every ${intervalSummary(ui.value, ui.unit)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (ui.snapped) {
            Text(
                "Adjusted from saved value ($intervalMinutes min). Saving will store the new interval.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

private fun intervalSummary(value: Int, unit: IntervalUnit): String = when (unit) {
    IntervalUnit.Minutes -> if (value == 1) "1 minute" else "$value minutes"
    IntervalUnit.Hours -> if (value == 1) "1 hour" else "$value hours"
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

        // ── Input Text Compaction ──
        Spacer(Modifier.height(4.dp))
        Text("Input Text Compaction", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        Text(
            "Reduce token usage by compacting fetched text before final AI processing. Your instructions are not changed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        InputCompactionMode.entries.forEach { mode ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = config.inputCompaction == mode,
                    onClick = { onConfigChanged(config.copy(inputCompaction = mode)) }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(mode.displayName, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        mode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Number of Outputs ──
        Spacer(Modifier.height(4.dp))
        Text("Number of Outputs", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        Text(
            "Generate multiple versions from the same input. Useful for alternative summaries, social posts, and experimenting with different phrasing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            (1..5).forEach { n ->
                FilterChip(
                    selected = config.numberOfOutputs == n,
                    onClick = { onConfigChanged(config.copy(numberOfOutputs = n)) },
                    label = { Text("$n") },
                    modifier = Modifier.height(32.dp)
                )
            }
        }
        if (config.numberOfOutputs > 3) {
            Text(
                "Generating many versions increases token usage and cost.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
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
    onSetup: (String) -> Unit
) {
    if (readiness == null) return
    // Don't show readiness bar for empty workflows — there's nothing to evaluate yet
    if (readiness.totalCount == 0 && readiness.profileIssues.isEmpty()) return

    val hasActionIssues = readiness.needsSetupActions.isNotEmpty()
    val hasProfileIssues = readiness.profileIssues.isNotEmpty()
    // If not fully ready but there are no actionable issues to show, treat as ready
    val effectivelyReady = readiness.isFullyReady || (!hasActionIssues && !hasProfileIssues)

    val containerColor = if (effectivelyReady)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)

    val contentColor = if (effectivelyReady)
        MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onErrorContainer

    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (effectivelyReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                when {
                    effectivelyReady -> {
                        Text("Ready to run", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = contentColor)
                    }
                    hasActionIssues -> {
                        val setupCount = readiness.needsSetupActions.size
                        Text(
                            "$setupCount ${if (setupCount == 1) "action needs" else "actions need"} setup",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = contentColor
                        )
                        val names = readiness.needsSetupActions.joinToString(", ") {
                            WorkflowActionRegistry.getInfo(it.type).displayName
                        }
                        Text(names, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.8f))
                    }
                    hasProfileIssues -> {
                        Text(
                            "AI provider needs setup",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = contentColor
                        )
                        Text(
                            readiness.profileIssues.first(),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.8f),
                            maxLines = 2
                        )
                    }
                }
            }
            if (!effectivelyReady) {
                // Route to the correct settings section based on the actual issue
                val section = if (hasProfileIssues && !hasActionIssues)
                    SettingsSection.PROVIDER_KEYS.key
                else
                    SettingsSection.SERVICE_KEYS.key
                TextButton(onClick = { onSetup(section) }) {
                    Text("Fix in Settings", color = contentColor)
                }
            }
        }
    }
}
