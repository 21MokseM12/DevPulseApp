package com.devpulse.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devpulse.app.data.local.preferences.QuietHoursPolicy
import com.devpulse.app.data.local.preferences.QuietHoursTimezoneMode
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

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Quiet hours schedule", style = MaterialTheme.typography.headlineSmall)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Включить quiet hours", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = policy.enabled,
                onCheckedChange = onQuietHoursEnabledChanged,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { onQuietHoursStartShifted(-30) }, enabled = controlsEnabled) {
                Text(text = "С -30")
            }
            OutlinedButton(onClick = { onQuietHoursStartShifted(30) }, enabled = controlsEnabled) {
                Text(text = "С +30")
            }
            Text(
                text = formatMinutes(policy.fromMinutes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { onQuietHoursEndShifted(-30) }, enabled = controlsEnabled) {
                Text(text = "До -30")
            }
            OutlinedButton(onClick = { onQuietHoursEndShifted(30) }, enabled = controlsEnabled) {
                Text(text = "До +30")
            }
            Text(
                text = formatMinutes(policy.toMinutes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

        Text(text = "Дни недели", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DayOfWeek.entries.forEach { day ->
                val selected = policy.weekdays.contains(day)
                OutlinedButton(
                    onClick = { onQuietHoursWeekdayToggled(day) },
                    enabled = controlsEnabled,
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

        Text(text = "Часовой пояс", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onQuietHoursTimezoneModeSelected(QuietHoursTimezoneMode.Device) },
                enabled = controlsEnabled,
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
                enabled = controlsEnabled,
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

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "Preview ближайшего окна", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Назад в настройки")
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
    return getDisplayName(TextStyle.SHORT, Locale("ru"))
}
