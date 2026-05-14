package com.devpulse.app.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsRoute(
    onGoToSubscriptions: () -> Unit,
    onGoToUpdates: () -> Unit,
    onNavigateToAuth: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    LaunchedEffect(uiState.shouldNavigateToAuth) {
        if (uiState.shouldNavigateToAuth) {
            onNavigateToAuth()
            viewModel.onAuthNavigationHandled()
        }
    }
    SettingsScreen(
        uiState = uiState,
        onGoToSubscriptions = onGoToSubscriptions,
        onGoToUpdates = onGoToUpdates,
        onPermissionRequestTriggered = viewModel::onPermissionRequestTriggered,
        onLogoutRequested = viewModel::onLogoutRequested,
        onUnregisterRequested = viewModel::onUnregisterRequested,
        onUnregisterDismissed = viewModel::onUnregisterDismissed,
        onUnregisterConfirmed = viewModel::onUnregisterConfirmed,
    )
}

@Composable
private fun SettingsScreen(
    uiState: SettingsUiState,
    onGoToSubscriptions: () -> Unit,
    onGoToUpdates: () -> Unit,
    onPermissionRequestTriggered: () -> Unit,
    onLogoutRequested: () -> Unit,
    onUnregisterRequested: () -> Unit,
    onUnregisterDismissed: () -> Unit,
    onUnregisterConfirmed: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val evaluator = remember { NotificationPermissionEvaluator() }
    var refreshTick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshTick += 1
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val requestPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshTick += 1
        }

    val hasRuntimePermission =
        if (refreshTick >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    val notificationsEnabled =
        if (refreshTick >= 0) NotificationManagerCompat.from(context).areNotificationsEnabled() else true
    val shouldShowRationale =
        if (refreshTick >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null) {
            activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            false
        }

    val permissionState =
        evaluator.resolve(
            sdkInt = Build.VERSION.SDK_INT,
            hasRuntimePermission = hasRuntimePermission,
            notificationsEnabled = notificationsEnabled,
            hasRequestedBefore = uiState.hasRequestedNotificationPermission,
            shouldShowRationale = shouldShowRationale,
        )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = permissionDescription(permissionState),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (permissionState) {
            NotificationPermissionState.NotRequired -> Unit
            NotificationPermissionState.Granted -> Unit
            NotificationPermissionState.NeedsRequest -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Button(
                        onClick = {
                            onPermissionRequestTriggered()
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Разрешить уведомления")
                    }
                }
            }

            NotificationPermissionState.NeedsSettings -> {
                Button(
                    onClick = { context.openNotificationSettings() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Открыть настройки уведомлений")
                }
            }
        }
        Button(onClick = onGoToSubscriptions, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Открыть Subscriptions")
        }
        Button(onClick = onGoToUpdates, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Открыть Updates")
        }
        Button(
            onClick = onLogoutRequested,
            enabled = uiState.logoutStatus != LogoutActionStatus.InProgress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text =
                    if (uiState.logoutStatus == LogoutActionStatus.InProgress) {
                        "Выход..."
                    } else {
                        "Выйти"
                    },
            )
        }
        OutlinedButton(
            onClick = onUnregisterRequested,
            enabled = uiState.unregisterStatus != UnregisterActionStatus.InProgress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text =
                    if (uiState.unregisterStatus == UnregisterActionStatus.InProgress) {
                        "Удаление аккаунта..."
                    } else {
                        "Удалить аккаунт"
                    },
            )
        }
        if (uiState.unregisterErrorMessage != null) {
            Text(
                text = uiState.unregisterErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (uiState.showUnregisterConfirmation) {
        AlertDialog(
            onDismissRequest = onUnregisterDismissed,
            title = { Text(text = "Удалить аккаунт?") },
            text = { Text(text = "Аккаунт будет удален на сервере, а локальные данные очищены.") },
            confirmButton = {
                Button(onClick = onUnregisterConfirmed) {
                    Text(text = "Удалить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onUnregisterDismissed) {
                    Text(text = "Отмена")
                }
            },
        )
    }
}

private fun permissionDescription(state: NotificationPermissionState): String {
    return when (state) {
        NotificationPermissionState.NotRequired -> "На этом Android runtime-разрешение не требуется."
        NotificationPermissionState.Granted -> "Уведомления включены."
        NotificationPermissionState.NeedsRequest -> "Нужен доступ к уведомлениям, чтобы показывать push локально."
        NotificationPermissionState.NeedsSettings ->
            "Разрешение отключено, включите уведомления в настройках приложения."
    }
}

private fun Context.openNotificationSettings() {
    val intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    runCatching { startActivity(intent) }.onFailure {
        val fallback =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        startActivity(fallback)
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
