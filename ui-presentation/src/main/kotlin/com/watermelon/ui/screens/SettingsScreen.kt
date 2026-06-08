package com.watermelon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Settings screen. Stateless over [SettingsState] + change callbacks — the host binds to
 * DataStore. All groups from Manifest §9.
 */
data class SettingsState(
    val pureDark: Boolean            = true,
    val forcedRtl: Boolean           = false,
    val gridDefault: Boolean         = false,
    val showThumbnails: Boolean      = true,
    val showDurations: Boolean       = true,
    val showFileSize: Boolean        = false,
    val vhsEnabled: Boolean          = true,
    val vhsIntensity: VhsIntensity   = VhsIntensity.MED,
    val memorySafety: Boolean        = false,
    val fullFolderAccess: Boolean    = false,
    // Player settings
    val screenshotMode: ScreenshotMode = ScreenshotMode.SINGLE
)

enum class VhsIntensity { OFF, LOW, MED, HIGH }

/** Controls whether the screenshot button captures one frame or a burst of nine. */
enum class ScreenshotMode { SINGLE, BURST }

@Composable
fun SettingsScreen(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { GroupHeader("Theme") }
        item { ToggleRow("Pure Dark", state.pureDark) { onStateChange(state.copy(pureDark = it)) } }
        item { ToggleRow("Forced RTL overrides", state.forcedRtl) { onStateChange(state.copy(forcedRtl = it)) } }
        item { HorizontalDivider() }

        item { GroupHeader("View Defaults") }
        item { ToggleRow("Grid layout default", state.gridDefault) { onStateChange(state.copy(gridDefault = it)) } }
        item { ToggleRow("Show thumbnails", state.showThumbnails) { onStateChange(state.copy(showThumbnails = it)) } }
        item { ToggleRow("Show durations", state.showDurations) { onStateChange(state.copy(showDurations = it)) } }
        item { ToggleRow("Show file size", state.showFileSize) { onStateChange(state.copy(showFileSize = it)) } }
        item { HorizontalDivider() }

        item { GroupHeader("VHS Effect") }
        item { ToggleRow("VHS enabled", state.vhsEnabled) { onStateChange(state.copy(vhsEnabled = it)) } }
        item {
            Text(
                "Intensity: ${state.vhsIntensity.name.lowercase()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            ToggleRow("Memory-safety (force Tier B)", state.memorySafety) {
                onStateChange(state.copy(memorySafety = it))
            }
        }
        item { HorizontalDivider() }

        item { GroupHeader("Player") }
        item {
            ToggleRow(
                label   = "Burst screenshot (4 frames before + current + 4 after)",
                checked = state.screenshotMode == ScreenshotMode.BURST
            ) {
                onStateChange(
                    state.copy(screenshotMode = if (it) ScreenshotMode.BURST else ScreenshotMode.SINGLE)
                )
            }
        }
        item { HorizontalDivider() }

        item { GroupHeader("System") }
        item {
            ToggleRow("Full folder access (power-user)", state.fullFolderAccess) {
                onStateChange(state.copy(fullFolderAccess = it))
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text       = title,
        style      = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.primary,
        modifier   = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
