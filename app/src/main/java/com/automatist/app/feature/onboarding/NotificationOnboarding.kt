package com.automatist.app.feature.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatist.app.data.local.SettingsRepository
import com.automatist.app.platform.notifications.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * First-run notification rationale dialog. Shown once on a fresh install because the
 * seeded News to Social workflow has start + completion notifications enabled by
 * default — the user deserves to know why the app is asking for permission.
 *
 * Flow:
 *  - If already onboarded, or API < 33, or permission already granted → not shown.
 *  - Allow: launches the system permission dialog.
 *  - Not now: dismisses and marks onboarding shown; editor screen can re-prompt later.
 *
 * After the system prompt, we only mark onboarding shown if the user took any action.
 */

@HiltViewModel
class NotificationOnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _shouldShow = MutableStateFlow(false)
    val shouldShow: StateFlow<Boolean> = _shouldShow.asStateFlow()

    init {
        viewModelScope.launch {
            val onboardingDone = settingsRepository.wasNotificationOnboardingShown()
            val permissionAlreadyGranted = !notificationHelper.needsNotificationPermissionRequest()
            _shouldShow.value = !onboardingDone && !permissionAlreadyGranted
        }
    }

    fun dismiss() {
        _shouldShow.value = false
        viewModelScope.launch { settingsRepository.markNotificationOnboardingShown() }
    }
}

@Composable
fun NotificationOnboardingGate(
    viewModel: NotificationOnboardingViewModel = hiltViewModel()
) {
    val shouldShow by viewModel.shouldShow.collectAsState()
    if (!shouldShow) return

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Whether granted or denied, the user has been through the system dialog once.
        // If denied, the in-editor banner and schedule-status banner will offer re-request.
        viewModel.dismiss()
    }

    AlertDialog(
        onDismissRequest = { viewModel.dismiss() },
        icon = {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Stay in the loop") },
        text = {
            Column {
                Text(
                    "Automatist comes with a sample \"News to Social Posts\" workflow that can " +
                        "run on a schedule. Allow notifications so you know when it starts " +
                        "and when a fresh draft is ready."
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "You can change this any time in each workflow's settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.dismiss()
                    }
                }
            ) { Text("Allow") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismiss() }) { Text("Not now") }
        }
    )
}

/**
 * Launches the Android per-app notification settings screen. Use this fallback when
 * the OS no longer shows the runtime permission dialog (user has selected "Don't ask
 * again" or permission is otherwise locked).
 */
fun openAppNotificationSettings(activity: Activity) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
    }
    activity.startActivity(intent)
}

/**
 * True when the system will still show the runtime-permission dialog if asked.
 * False means the OS has locked permission (user chose "Don't ask again" or equivalent)
 * and we should surface an "Open settings" CTA instead.
 */
fun canRequestNotificationPermission(activity: Activity, alreadyAsked: Boolean): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
        activity, Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (granted) return false
    val shouldShowRationale =
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
    // First-ever request: shouldShowRationale is false AND alreadyAsked is false → system will show dialog.
    // After one denial: shouldShowRationale is true → system will show again.
    // Permanently denied: shouldShowRationale is false AND alreadyAsked is true → system won't show; need settings.
    return !alreadyAsked || shouldShowRationale
}
