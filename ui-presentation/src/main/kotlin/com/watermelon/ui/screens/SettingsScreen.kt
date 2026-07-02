package com.watermelon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable

/**
 * Settings screen. Stateless over [SettingsState] + change callbacks — the host binds to
 * DataStore. Layout matches wireframe #6: a titled header, then grouped "cards", each with
 * a small colored eyebrow label above it. Folder visibility is a navigation row.
 *
 * This is a LAYOUT rebuild only — SettingsState and all callbacks are unchanged.
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
    val screenshotMode: ScreenshotMode = ScreenshotMode.SINGLE,
    val folderVisibility: Map<String, Boolean> = emptyMap(),
    val subtitleStyle: com.watermelon.common.model.SubtitleStyle = com.watermelon.common.model.SubtitleStyle()
)

enum class VhsIntensity { OFF, LOW, MED, HIGH }

/** Controls whether the screenshot button captures one frame or a burst of nine. */
enum class ScreenshotMode { SINGLE, BURST }

@Composable
fun SettingsScreen(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit,
    onFolderVisibilityClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Screen title
        item {
            Text(
                text       = "Settings",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // ── Appearance ──────────────────────────────────────────────────────
        item {
            SettingsGroup("APPEARANCE") {
                ToggleRow("Pure dark theme", state.pureDark) {
                    onStateChange(state.copy(pureDark = it))
                }
                ToggleRow("Force RTL overrides", state.forcedRtl) {
                    onStateChange(state.copy(forcedRtl = it))
                }
            }
        }

        // ── View defaults ───────────────────────────────────────────────────
        item {
            SettingsGroup("VIEW DEFAULTS") {
                ToggleRow("Grid layout by default", state.gridDefault) {
                    onStateChange(state.copy(gridDefault = it))
                }
                ToggleRow("Show thumbnails", state.showThumbnails) {
                    onStateChange(state.copy(showThumbnails = it))
                }
                ToggleRow("Show durations", state.showDurations) {
                    onStateChange(state.copy(showDurations = it))
                }
                ToggleRow("Show file size", state.showFileSize) {
                    onStateChange(state.copy(showFileSize = it))
                }
            }
        }

        // ── Player ──────────────────────────────────────────────────────────
        item {
            SettingsGroup("PLAYER") {
                ToggleRow(
                    label   = "Burst screenshot (9 frames)",
                    checked = state.screenshotMode == ScreenshotMode.BURST
                ) {
                    onStateChange(
                        state.copy(screenshotMode = if (it) ScreenshotMode.BURST else ScreenshotMode.SINGLE)
                    )
                }
                ToggleRow("VHS effect", state.vhsEnabled) {
                    onStateChange(state.copy(vhsEnabled = it))
                }
                NavRow(
                    label = "VHS intensity",
                    value = state.vhsIntensity.name.lowercase().replaceFirstChar { it.uppercase() },
                    onClick = {
                        // Cycle intensity OFF -> LOW -> MED -> HIGH -> OFF
                        val next = VhsIntensity.values()[(state.vhsIntensity.ordinal + 1) % VhsIntensity.values().size]
                        onStateChange(state.copy(vhsIntensity = next))
                    }
                )
            }
        }

        // ── Subtitles ────────────────────────────────────────────────────────
        item {
            SettingsGroup("SUBTITLES") {
                val st = state.subtitleStyle
                fun up(new: com.watermelon.common.model.SubtitleStyle) =
                    onStateChange(state.copy(subtitleStyle = new))

                ToggleRow("Enable subtitles", st.enabled) { up(st.copy(enabled = it)) }
                StepperRow("Text size", "${st.sizeSp}sp",
                    onMinus = { up(st.copy(sizeSp = (st.sizeSp - 2).coerceAtLeast(12))) },
                    onPlus  = { up(st.copy(sizeSp = (st.sizeSp + 2).coerceAtMost(48))) })
                NavRow("Text color", subtitleColorName(st.textColorArgb), onClick = {
                    up(st.copy(textColorArgb = nextSubtitleColor(st.textColorArgb)))
                })
                NavRow("Position", st.position.name.lowercase().replaceFirstChar { it.uppercase() }, onClick = {
                    up(st.copy(position = if (st.position == com.watermelon.common.model.SubtitlePosition.BOTTOM)
                        com.watermelon.common.model.SubtitlePosition.TOP
                    else com.watermelon.common.model.SubtitlePosition.BOTTOM))
                })
                ToggleRow("Bold", st.bold) { up(st.copy(bold = it)) }
                ToggleRow("Italic", st.italic) { up(st.copy(italic = it)) }
                ToggleRow("Underline", st.underline) { up(st.copy(underline = it)) }
                NavRow("Direction", st.direction.label(), onClick = {
                    up(st.copy(direction = st.direction.next()))
                })
                NavRow("2nd sub direction", st.secondaryDirection.label(), onClick = {
                    up(st.copy(secondaryDirection = st.secondaryDirection.next()))
                })
            }
        }

        // ── System ──────────────────────────────────────────────────────────
        item {
            SettingsGroup("SYSTEM") {
                ToggleRow("Memory-safety (force Tier B)", state.memorySafety) {
                    onStateChange(state.copy(memorySafety = it))
                }
                ToggleRow("Full folder access (power-user)", state.fullFolderAccess) {
                    onStateChange(state.copy(fullFolderAccess = it))
                }
                NavRow(
                    label = "Folder visibility",
                    value = "Manage",
                    accent = true,
                    onClick = onFolderVisibilityClick
                )
            }
        }
    }
}

/** A titled card group: small colored eyebrow label, then a rounded surface of rows. */
@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text       = title,
            color      = MaterialTheme.colorScheme.primary,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(start = 4.dp)
        )
        Surface(
            shape  = RoundedCornerShape(12.dp),
            color  = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A row that shows a value and navigates / cycles on tap. */
@Composable
private fun NavRow(label: String, value: String, onClick: () -> Unit, accent: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = "$value ›",
            style = MaterialTheme.typography.bodyMedium,
            color = if (accent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}


// ── Subtitle settings helpers ───────────────────────────────────────────────

private val SUBTITLE_COLORS = listOf(
    0xFFFFFFFF to "White", 0xFFFFEB3B to "Yellow", 0xFF00E5FF to "Cyan",
    0xFF69F0AE to "Green", 0xFFFF8A80 to "Coral", 0xFF000000 to "Black"
)

private fun subtitleColorName(argb: Long): String =
    SUBTITLE_COLORS.firstOrNull { it.first == argb }?.second ?: "Custom"

private fun nextSubtitleColor(argb: Long): Long {
    val i = SUBTITLE_COLORS.indexOfFirst { it.first == argb }
    return SUBTITLE_COLORS[(i + 1) % SUBTITLE_COLORS.size].first
}

private fun com.watermelon.common.model.SubtitleDirection.label(): String = when (this) {
    com.watermelon.common.model.SubtitleDirection.AUTO -> "Auto"
    com.watermelon.common.model.SubtitleDirection.FORCE_RTL -> "Force RTL"
    com.watermelon.common.model.SubtitleDirection.FORCE_LTR -> "Force LTR"
}

private fun com.watermelon.common.model.SubtitleDirection.next(): com.watermelon.common.model.SubtitleDirection {
    val v = com.watermelon.common.model.SubtitleDirection.values()
    return v[(ordinal + 1) % v.size]
}

/** Row with -/+ steppers for numerical values. */
@Composable
private fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onMinus) { Text("−", fontSize = 20.sp) }
            Text(value, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onPlus) { Text("+", fontSize = 20.sp) }
        }
    }
}