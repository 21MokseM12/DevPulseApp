package com.devpulse.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devpulse.app.data.local.preferences.QuietHoursPolicy
import com.devpulse.app.data.local.preferences.QuietHoursTimezoneMode
import com.devpulse.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun QuietHoursScheduleRoute(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    QuietHoursScheduleScreen(
        policy = uiState.notificationPreferences.quietHoursPolicy,
        onNavigateBack = onNavigateBack,
        onQuietHoursEnabledChanged = viewModel::onQuietHoursEnabledChanged,
        onQuietHoursStartShifted = viewModel::onQuietHoursStartShifted,
        onQuietHoursEndShifted = viewModel::onQuietHoursEndShifted,
        onQuietHoursWeekdayToggled = viewModel::onQuietHoursWeekdayToggled,
        onQuietHoursTimezoneModeSelected = viewModel::onQuietHoursTimezoneModeSelected,
    )
}

@Composable
internal fun QuietHoursScheduleScreen(
    policy: QuietHoursPolicy,
    onNavigateBack: () -> Unit,
    onQuietHoursEnabledChanged: (Boolean) -> Unit,
    onQuietHoursStartShifted: (Int) -> Unit,
    onQuietHoursEndShifted: (Int) -> Unit,
    onQuietHoursWeekdayToggled: (DayOfWeek) -> Unit,
    onQuietHoursTimezoneModeSelected: (QuietHoursTimezoneMode) -> Unit,
) {
    val now by produceState(initialValue = Instant.now(), key1 = policy) {
        while (true) {
            value = Instant.now()
            delay(30_000)
        }
    }
    val preview = formatQuietHoursPreview(policy = policy, now = now)
    val controlsEnabled = policy.enabled

    BackHandler(onBack = onNavigateBack)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Enable toggle card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
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
                )
            }
        }

        // Time range card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    text = "Временной диапазон",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TimeStepperRow(
                    label = "Начало",
                    value = formatMinutes(policy.fromMinutes),
                    enabled = controlsEnabled,
                    onDecrement = { onQuietHoursStartShifted(-30) },
                    onIncrement = { onQuietHoursStartShifted(30) },
                )

                TimeStepperRow(
                    label = "Конец",
                    value = formatMinutes(policy.toMinutes),
                    enabled = controlsEnabled,
                    onDecrement = { onQuietHoursEndShifted(-30) },
                    onIncrement = { onQuietHoursEndShifted(30) },
                )
            }
        }

        // Weekdays card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = "Дни недели",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    DayOfWeek.entries.forEach { day ->
                        val selected = policy.weekdays.contains(day)
                        FilterChip(
                            selected = selected,
                            onClick = { onQuietHoursWeekdayToggled(day) },
                            enabled = controlsEnabled,
                            label = {
                                Text(
                                    text = day.shortLabel(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
            }
        }

        // Timezone card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = "Часовой пояс",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    FilterChip(
                        selected = policy.timezoneMode == QuietHoursTimezoneMode.Device,
                        onClick = { onQuietHoursTimezoneModeSelected(QuietHoursTimezoneMode.Device) },
                        enabled = controlsEnabled,
                        label = { Text("Устройство") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = policy.timezoneMode == QuietHoursTimezoneMode.Fixed,
                        onClick = { onQuietHoursTimezoneModeSelected(QuietHoursTimezoneMode.Fixed) },
                        enabled = controlsEnabled,
                        label = { Text("Фиксированный UTC") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Preview card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = "Ближайшее окно",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TimeStepperRow(
    label: String,
    value: String,
    enabled: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onDecrement,
                enabled = enabled,
            ) {
                Text("−")
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedButton(
                onClick = onIncrement,
                enabled = enabled,
            ) {
                Text("+")
            }
        }
    }
}

private fun formatMinutes(minutes: Int): String {
    val normalized = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60)
    val hours = normalized / 60
    val mins = normalized % 60
    return "%02d:%02d".format(hours, mins)
}

private fun DayOfWeek.shortLabel(): String {
    return getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("ru"))
}
