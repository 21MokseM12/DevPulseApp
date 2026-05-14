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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import com.devpulse.app.data.local.preferences.NotificationDigestMode
import com.devpulse.app.data.local.preferences.NotificationPresentationMode
import com.devpulse.app.data.local.preferences.QuietHoursPolicy
import com.devpulse.app.data.local.preferences.QuietHoursTimezoneMode
import com.devpulse.app.push.PushNotificationTextResolver
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

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
        onNotificationToggleChanged = viewModel::onNotificationToggleChanged,
        onNotificationPresentationModeSelected = viewModel::onNotificationPresentationModeSelected,
        onNotificationDigestModeToggled = viewModel::onNotificationDigestModeToggled,
        onQuietHoursEnabledChanged = viewModel::onQuietHoursEnabledChanged,
        onQuietHoursStartShifted = viewModel::onQuietHoursStartShifted,
        onQuietHoursEndShifted = viewModel::onQuietHoursEndShifted,
        onQuietHoursWeekdayToggled = viewModel::onQuietHoursWeekdayToggled,
        onQuietHoursTimezoneModeSelected = viewModel::onQuietHoursTimezoneModeSelected,
        onSystemNotificationCapabilityChanged = viewModel::onSystemNotificationCapabilityChanged,
        onLogoutRequested = viewModel::onLogoutRequested,
        onUnregisterRequested = viewModel::onUnregisterRequested,
        onUnregisterDismissed = viewModel::onUnregisterDismissed,
        onUnregisterConfirmed = viewModel::onUnregisterConfirmed,
    )
}

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onGoToSubscriptions: () -> Unit,
    onGoToUpdates: () -> Unit,
    onPermissionRequestTriggered: () -> Unit,
    onNotificationToggleChanged: (Boolean) -> Unit,
    onNotificationPresentationModeSelected: (NotificationPresentationMode) -> Unit,
    onNotificationDigestModeToggled: (Boolean) -> Unit,
    onQuietHoursEnabledChanged: (Boolean) -> Unit,
    onQuietHoursStartShifted: (Int) -> Unit,
    onQuietHoursEndShifted: (Int) -> Unit,
    onQuietHoursWeekdayToggled: (DayOfWeek) -> Unit,
    onQuietHoursTimezoneModeSelected: (QuietHoursTimezoneMode) -> Unit,
    onSystemNotificationCapabilityChanged: (Boolean) -> Unit,
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
    val canPostSystemNotifications =
        permissionState == NotificationPermissionState.NotRequired ||
            permissionState == NotificationPermissionState.Granted
    val effectiveNotificationsEnabled = uiState.notificationPreferences.enabled && canPostSystemNotifications
    val textResolver = remember { PushNotificationTextResolver() }

    LaunchedEffect(canPostSystemNotifications) {
        onSystemNotificationCapabilityChanged(canPostSystemNotifications)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Показывать системные уведомления",
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = effectiveNotificationsEnabled,
                onCheckedChange = { isEnabled ->
                    if (!isEnabled) {
                        onNotificationToggleChanged(false)
                    } else if (canPostSystemNotifications) {
                        onNotificationToggleChanged(true)
                    } else {
                        when (permissionState) {
                            NotificationPermissionState.NeedsRequest -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    onPermissionRequestTriggered()
                                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            NotificationPermissionState.NeedsSettings -> context.openNotificationSettings()
                            NotificationPermissionState.NotRequired -> onNotificationToggleChanged(true)
                            NotificationPermissionState.Granted -> onNotificationToggleChanged(true)
                        }
                    }
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { onNotificationPresentationModeSelected(NotificationPresentationMode.Compact) },
                enabled = effectiveNotificationsEnabled,
                modifier = Modifier,
            ) {
                Text(
                    text =
                        if (uiState.notificationPreferences.presentationMode == NotificationPresentationMode.Compact) {
                            "Compact ✓"
                        } else {
                            "Compact"
                        },
                )
            }
            OutlinedButton(
                onClick = { onNotificationPresentationModeSelected(NotificationPresentationMode.Detailed) },
                enabled = effectiveNotificationsEnabled,
                modifier = Modifier,
            ) {
                Text(
                    text =
                        if (uiState.notificationPreferences.presentationMode == NotificationPresentationMode.Detailed) {
                            "Detailed ✓"
                        } else {
                            "Detailed"
                        },
                )
            }
        }
        Text(
            text =
                when (uiState.notificationPreferences.presentationMode) {
                    NotificationPresentationMode.Compact ->
                        "Compact: короткий текст в шторке без деталей."
                    NotificationPresentationMode.Detailed ->
                        "Detailed: полный текст обновления в уведомлении."
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Digest mode (daily)",
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = uiState.notificationPreferences.digestMode != null,
                enabled = effectiveNotificationsEnabled,
                onCheckedChange = onNotificationDigestModeToggled,
            )
        }
        NotificationPreviewCard(
            presentationMode = uiState.notificationPreferences.presentationMode,
            digestMode = uiState.notificationPreferences.digestMode,
            textResolver = textResolver,
            notificationsEnabled = effectiveNotificationsEnabled,
        )
        QuietHoursCard(
            policy = uiState.notificationPreferences.quietHoursPolicy,
            enabled = effectiveNotificationsEnabled,
            onQuietHoursEnabledChanged = onQuietHoursEnabledChanged,
            onQuietHoursStartShifted = onQuietHoursStartShifted,
            onQuietHoursEndShifted = onQuietHoursEndShifted,
            onQuietHoursWeekdayToggled = onQuietHoursWeekdayToggled,
            onQuietHoursTimezoneModeSelected = onQuietHoursTimezoneModeSelected,
        )
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

@Composable
private fun QuietHoursCard(
    policy: QuietHoursPolicy,
    enabled: Boolean,
    onQuietHoursEnabledChanged: (Boolean) -> Unit,
    onQuietHoursStartShifted: (Int) -> Unit,
    onQuietHoursEndShifted: (Int) -> Unit,
    onQuietHoursWeekdayToggled: (DayOfWeek) -> Unit,
    onQuietHoursTimezoneModeSelected: (QuietHoursTimezoneMode) -> Unit,
) {
    val nextWindowPreview = remember(policy) { quietHoursPreview(policy, Instant.now()) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Quiet hours", style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = policy.enabled,
                    onCheckedChange = onQuietHoursEnabledChanged,
                    enabled = enabled,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onQuietHoursStartShifted(30) },
                    enabled = enabled && policy.enabled,
                ) {
                    Text(text = "С ${formatMinutes(policy.fromMinutes)}")
                }
                OutlinedButton(
                    onClick = { onQuietHoursEndShifted(30) },
                    enabled = enabled && policy.enabled,
                ) {
                    Text(text = "До ${formatMinutes(policy.toMinutes)}")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DayOfWeek.entries.forEach { day ->
                    val selected = policy.weekdays.contains(day)
                    OutlinedButton(
                        onClick = { onQuietHoursWeekdayToggled(day) },
                        enabled = enabled && policy.enabled,
                    ) {
                        Text(
                            text = day.shortLabel(),
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onQuietHoursTimezoneModeSelected(QuietHoursTimezoneMode.Device) },
                    enabled = enabled && policy.enabled,
                ) {
                    Text(
                        text =
                            if (policy.timezoneMode == QuietHoursTimezoneMode.Device) {
                                "Часовой пояс устройства ✓"
                            } else {
                                "Часовой пояс устройства"
                            },
                    )
                }
                OutlinedButton(
                    onClick = { onQuietHoursTimezoneModeSelected(QuietHoursTimezoneMode.Fixed) },
                    enabled = enabled && policy.enabled,
                ) {
                    Text(
                        text =
                            if (policy.timezoneMode == QuietHoursTimezoneMode.Fixed) {
                                "Фиксированный UTC ✓"
                            } else {
                                "Фиксированный UTC"
                            },
                    )
                }
            }
            Text(
                text = nextWindowPreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun NotificationPreviewCard(
    presentationMode: NotificationPresentationMode,
    digestMode: NotificationDigestMode?,
    textResolver: PushNotificationTextResolver,
    notificationsEnabled: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Preview уведомления",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = PushNotificationTextResolver.DEFAULT_NOTIFICATION_TITLE,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text =
                    if (!notificationsEnabled) {
                        "Системные уведомления отключены."
                    } else {
                        textResolver.resolvePreviewBody(
                            presentationMode = presentationMode,
                            digestMode = digestMode,
                        )
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    if (digestMode == null) {
                        "Режим: ${presentationMode.name.lowercase()}"
                    } else {
                        "Режим: digest (${digestMode.name.lowercase()})"
                    },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun quietHoursPreview(
    policy: QuietHoursPolicy,
    now: Instant,
): String {
    if (!policy.enabled) return "Quiet hours выключены."
    val zoneId =
        if (policy.timezoneMode == QuietHoursTimezoneMode.Device) {
            ZoneId.systemDefault()
        } else {
            policy.resolvedFixedZoneId?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()
        }
    val currentDay = now.atZone(zoneId).dayOfWeek
    val weekdaysPreview =
        policy.weekdays
            .sortedBy { it.value }
            .joinToString(separator = ", ") { it.shortLabel() }
    return buildString {
        append("Следующее тихое окно: ")
        append(formatMinutes(policy.fromMinutes))
        append("–")
        append(formatMinutes(policy.toMinutes))
        append(" (")
        append(zoneId.id)
        append("). Дни: ")
        append(weekdaysPreview)
        append(". Сегодня: ")
        append(currentDay.shortLabel())
        append(".")
    }
}

private fun formatMinutes(minutes: Int): String {
    val normalized = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60)
    val hours = normalized / 60
    val mins = normalized % 60
    return "%02d:%02d".format(hours, mins)
}

private fun DayOfWeek.shortLabel(): String {
    return getDisplayName(TextStyle.SHORT, Locale("ru"))
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
