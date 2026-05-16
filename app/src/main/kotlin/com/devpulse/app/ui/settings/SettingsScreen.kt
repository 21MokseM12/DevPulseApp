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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devpulse.app.data.local.preferences.AppThemeMode
import com.devpulse.app.data.local.preferences.NotificationDigestMode
import com.devpulse.app.data.local.preferences.NotificationPresentationMode
import com.devpulse.app.data.local.preferences.QuietHoursPolicy
import com.devpulse.app.push.PushNotificationTextResolver
import com.devpulse.app.ui.testing.SmokeTestTags
import com.devpulse.app.ui.theme.Spacing
import java.time.Instant

@Composable
fun SettingsRoute(
    onGoToSubscriptions: () -> Unit,
    onGoToUpdates: () -> Unit,
    onOpenQuietHoursSchedule: () -> Unit,
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
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onOpenQuietHoursSchedule = onOpenQuietHoursSchedule,
        onPermissionRequestTriggered = viewModel::onPermissionRequestTriggered,
        onNotificationToggleChanged = viewModel::onNotificationToggleChanged,
        onNotificationPresentationModeSelected = viewModel::onNotificationPresentationModeSelected,
        onNotificationDigestModeToggled = viewModel::onNotificationDigestModeToggled,
        onNotificationDigestModeSelected = viewModel::onNotificationDigestModeSelected,
        onQuietHoursEnabledChanged = viewModel::onQuietHoursEnabledChanged,
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
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onOpenQuietHoursSchedule: () -> Unit,
    onPermissionRequestTriggered: () -> Unit,
    onNotificationToggleChanged: (Boolean) -> Unit,
    onNotificationPresentationModeSelected: (NotificationPresentationMode) -> Unit,
    onNotificationDigestModeToggled: (Boolean) -> Unit,
    onNotificationDigestModeSelected: (NotificationDigestMode) -> Unit,
    onQuietHoursEnabledChanged: (Boolean) -> Unit,
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
                if (event == Lifecycle.Event.ON_RESUME) refreshTick += 1
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // --- Внешний вид ---
        ThemeSectionCard(
            selectedMode = uiState.appThemeMode,
            onModeSelected = onThemeModeSelected,
        )

        // --- Уведомления ---
        SettingsSectionCard(title = "Уведомления") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Показывать системные уведомления",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
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
                    modifier = Modifier.testTag(SmokeTestTags.SETTINGS_NOTIFICATIONS_SWITCH),
                )
            }

            Text(
                text = permissionDescription(permissionState),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (permissionState) {
                NotificationPermissionState.NotRequired, NotificationPermissionState.Granted -> Unit
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
        }

        // --- Формат уведомлений ---
        SettingsSectionCard(title = "Формат уведомлений") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                DigestModeButton(
                    title = "Кратко",
                    selected = uiState.notificationPreferences.presentationMode == NotificationPresentationMode.Compact,
                    enabled = effectiveNotificationsEnabled,
                    onClick = { onNotificationPresentationModeSelected(NotificationPresentationMode.Compact) },
                    modifier = Modifier.weight(1f),
                )
                DigestModeButton(
                    title = "Подробно",
                    selected =
                        uiState.notificationPreferences.presentationMode == NotificationPresentationMode.Detailed,
                    enabled = effectiveNotificationsEnabled,
                    onClick = { onNotificationPresentationModeSelected(NotificationPresentationMode.Detailed) },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text =
                    when (uiState.notificationPreferences.presentationMode) {
                        NotificationPresentationMode.Compact -> "Кратко: короткий текст в шторке без деталей."
                        NotificationPresentationMode.Detailed -> "Подробно: полный текст обновления в уведомлении."
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // --- Дайджест ---
        SettingsSectionCard(title = "Дайджест") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Включить дайджест",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = uiState.notificationPreferences.digestMode != null,
                    enabled = effectiveNotificationsEnabled,
                    onCheckedChange = onNotificationDigestModeToggled,
                )
            }
            if (uiState.notificationPreferences.digestMode != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    DigestModeButton(
                        title = "Каждый час",
                        selected = uiState.notificationPreferences.digestMode == NotificationDigestMode.Hourly,
                        enabled = effectiveNotificationsEnabled,
                        onClick = { onNotificationDigestModeSelected(NotificationDigestMode.Hourly) },
                        modifier = Modifier.weight(1f),
                    )
                    DigestModeButton(
                        title = "6 часов",
                        selected = uiState.notificationPreferences.digestMode == NotificationDigestMode.EverySixHours,
                        enabled = effectiveNotificationsEnabled,
                        onClick = { onNotificationDigestModeSelected(NotificationDigestMode.EverySixHours) },
                        modifier = Modifier.weight(1f),
                    )
                    DigestModeButton(
                        title = "Раз в день",
                        selected = uiState.notificationPreferences.digestMode == NotificationDigestMode.Daily,
                        enabled = effectiveNotificationsEnabled,
                        onClick = { onNotificationDigestModeSelected(NotificationDigestMode.Daily) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // --- Preview уведомления ---
        NotificationPreviewCard(
            presentationMode = uiState.notificationPreferences.presentationMode,
            digestMode = uiState.notificationPreferences.digestMode,
            textResolver = textResolver,
            notificationsEnabled = effectiveNotificationsEnabled,
        )

        // --- Тихие часы ---
        QuietHoursCard(
            policy = uiState.notificationPreferences.quietHoursPolicy,
            enabled = effectiveNotificationsEnabled,
            onQuietHoursEnabledChanged = onQuietHoursEnabledChanged,
            onOpenQuietHoursSchedule = onOpenQuietHoursSchedule,
        )

        // --- Аккаунт ---
        SettingsSectionCard(title = "Аккаунт") {
            Button(
                onClick = onLogoutRequested,
                enabled = uiState.logoutStatus != LogoutActionStatus.InProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (uiState.logoutStatus == LogoutActionStatus.InProgress) "Выход..." else "Выйти",
                )
            }
            OutlinedButton(
                onClick = onUnregisterRequested,
                enabled = uiState.unregisterStatus != UnregisterActionStatus.InProgress,
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))
    }

    if (uiState.showUnregisterConfirmation) {
        AlertDialog(
            onDismissRequest = onUnregisterDismissed,
            title = { Text(text = "Удалить аккаунт?") },
            text = {
                Text(
                    text = "Аккаунт будет удален на сервере, а локальные данные очищены.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = onUnregisterConfirmed,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
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
private fun ThemeSectionCard(
    selectedMode: AppThemeMode,
    onModeSelected: (AppThemeMode) -> Unit,
) {
    SettingsSectionCard(title = "Внешний вид") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ThemeModeButton(
                label = "Системная",
                selected = selectedMode == AppThemeMode.SYSTEM,
                onClick = { onModeSelected(AppThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f),
            )
            ThemeModeButton(
                label = "Светлая",
                selected = selectedMode == AppThemeMode.LIGHT,
                onClick = { onModeSelected(AppThemeMode.LIGHT) },
                modifier = Modifier.weight(1f),
            )
            ThemeModeButton(
                label = "Тёмная",
                selected = selectedMode == AppThemeMode.DARK,
                onClick = { onModeSelected(AppThemeMode.DARK) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.sm),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.sm),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun DigestModeButton(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        ) {
            Text(text = title)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        ) {
            Text(text = title)
        }
    }
}

@Composable
private fun QuietHoursCard(
    policy: QuietHoursPolicy,
    enabled: Boolean,
    onQuietHoursEnabledChanged: (Boolean) -> Unit,
    onOpenQuietHoursSchedule: () -> Unit,
) {
    val nextWindowPreview = formatQuietHoursPreview(policy = policy, now = Instant.now())
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = "Тихие часы",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Включить тихие часы",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = policy.enabled,
                    onCheckedChange = onQuietHoursEnabledChanged,
                    enabled = enabled,
                )
            }
            Text(
                text = nextWindowPreview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onOpenQuietHoursSchedule,
                enabled = enabled,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(SmokeTestTags.SETTINGS_OPEN_QUIET_HOURS_BUTTON),
            ) {
                Text(text = "Изменить расписание")
            }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = "Предпросмотр уведомления",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = PushNotificationTextResolver.DEFAULT_NOTIFICATION_TITLE,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
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
                        when (presentationMode) {
                            NotificationPresentationMode.Compact -> "Режим: кратко"
                            NotificationPresentationMode.Detailed -> "Режим: подробно"
                        }
                    } else {
                        when (digestMode) {
                            NotificationDigestMode.Hourly -> "Режим: дайджест (каждый час)"
                            NotificationDigestMode.EverySixHours -> "Режим: дайджест (каждые 6 часов)"
                            NotificationDigestMode.Daily -> "Режим: дайджест (раз в день)"
                        }
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
