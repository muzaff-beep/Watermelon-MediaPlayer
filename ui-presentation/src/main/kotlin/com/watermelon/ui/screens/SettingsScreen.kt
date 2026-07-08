package com.watermelon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watermelon.ui.components.WatermelonHeader
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

/**
 * Settings screen. Stateless over [SettingsState] + change callbacks — the host binds to
 * DataStore. Layout matches wireframe #6: a titled header, then grouped "cards", each with
 * a small colored eyebrow label above it. Folder visibility is a navigation row.
 *
 * This is a LAYOUT rebuild only — SettingsState and all callbacks are unchanged.
 */
data class SettingsState(
    val pureDark: Boolean = true,
    val forcedRtl: Boolean = false,
    val gridDefault: Boolean = false,
    val showThumbnails: Boolean = true,
    val showDurations: Boolean = true,
    val showFileSize: Boolean = false,
    val vhsEnabled: Boolean = true,
    val vhsIntensity: VhsIntensity = VhsIntensity.MED,
    val memorySafety: Boolean = false,
    val fullFolderAccess: Boolean = false,
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
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Header with logo and branding
        WatermelonHeader(
            title = "Settings",
            showBackButton = true,
            onBackClick = onBack,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(WatermelonSpacing.md))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = WatermelonSpacing.md,
                vertical = 0.dp
            ),
            verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.md)
        ) {
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
                        label = "Burst screenshot (9 frames)",
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
                        options = VhsIntensity.values().map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                        onSelect = { selected ->
                            val next = VhsIntensity.values().first {
                                it.name.lowercase().replaceFirstChar { c -> c.uppercase() } == selected
                            }
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
                        onPlus = { up(st.copy(sizeSp = (st.sizeSp + 2).coerceAtMost(48))) })
                    NavRow(
                        label = "Text color",
                        value = subtitleColorName(st.textColorArgb),
                        options = SUBTITLE_COLORS.map { it.second },
                        onSelect = { selected ->
                            val argb = SUBTITLE_COLORS.first { it.second == selected }.first
                            up(st.copy(textColorArgb = argb))
                        }
                    )
                    NavRow(
                        label = "Position",
                        value = st.position.name.lowercase().replaceFirstChar { it.uppercase() },
                        options = com.watermelon.common.model.SubtitlePosition.values()
                            .map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                        onSelect = { selected ->
                            val next = com.watermelon.common.model.SubtitlePosition.values().first {
                                it.name.lowercase().replaceFirstChar { c -> c.uppercase() } == selected
                            }
                            up(st.copy(position = next))
                        }
                    )
                    ToggleRow("Bold", st.bold) { up(st.copy(bold = it)) }
                    ToggleRow("Italic", st.italic) { up(st.copy(italic = it)) }
                    ToggleRow("Underline", st.underline) { up(st.copy(underline = it)) }
                    NavRow(
                        label = "Direction",
                        value = st.direction.label(),
                        options = com.watermelon.common.model.SubtitleDirection.values().map { it.label() },
                        onSelect = { selected ->
                            val next = com.watermelon.common.model.SubtitleDirection.values().first { it.label() == selected }
                            up(st.copy(direction = next))
                        }
                    )
                    NavRow(
                        label = "2nd sub direction",
                        value = st.secondaryDirection.label(),
                        options = com.watermelon.common.model.SubtitleDirection.values().map { it.label() },
                        onSelect = { selected ->
                            val next = com.watermelon.common.model.SubtitleDirection.values().first { it.label() == selected }
                            up(st.copy(secondaryDirection = next))
                        }
                    )
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
}

/** A titled card group: small colored eyebrow label, then a rounded surface of rows. */
@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs)) {
        Text(
            text = title,
            color = WatermelonColors.Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = WatermelonSpacing.xs)
        )
        Surface(
            shape = RoundedCornerShape(WatermelonSpacing.control),
            color = WatermelonColors.DarkSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(
                horizontal = WatermelonSpacing.md,
                vertical = WatermelonSpacing.xs
            )) {
                content()
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onChange,
                role = Role.Switch
            )
            .padding(vertical = WatermelonSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = WatermelonTypography.typography.bodyLarge,
            color = WatermelonColors.DarkOnSurface,
            modifier = Modifier.weight(1f).padding(end = WatermelonSpacing.md)
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = MaterialTheme.colorScheme.primary
        )
    }
}

/** A row that shows a value and opens a dropdown menu of [options] on tap. */
@Composable
private fun NavRow(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    accent: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = true }
                .padding(vertical = WatermelonSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = WatermelonTypography.typography.bodyLarge,
                color = WatermelonColors.DarkOnSurface,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = WatermelonTypography.typography.bodyMedium,
                    color = WatermelonColors.DarkOnSurfaceVariant,
                    fontWeight = if (accent) FontWeight.Bold else FontWeight.Normal
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = WatermelonColors.DarkOnSurfaceVariant
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(
                        option,
                        style = WatermelonTypography.typography.bodyMedium,
                        color = WatermelonColors.DarkOnSurface
                    ) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** A navigation-only row (no dropdown) — used for links like "Folder visibility". */
@Composable
private fun NavRow(label: String, value: String, onClick: () -> Unit, accent: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = WatermelonSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = WatermelonTypography.typography.bodyLarge,
            color = WatermelonColors.DarkOnSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$value >",
            style = WatermelonTypography.typography.bodyMedium,
            color = WatermelonColors.DarkOnSurfaceVariant,
            fontWeight = if (accent) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// Add missing import for clickable
import androidx.compose.foundation.clickable

// ── Subtitle settings helpers ───────────────────────────────────────────────

private val SUBTITLE_COLORS = listOf(
    0xFFFFFFFF to "White", 0xFFFFEB3B to "Yellow", 0xFF00E5FF to "Cyan",
    0xFF69F0AE to "Green", 0xFFFF8A80 to "Coral", 0xFF000000 to "Black"
)

private fun subtitleColorName(argb: Long): String =
    SUBTITLE_COLORS.firstOrNull { it.first == argb }?.second ?: "Custom"

private fun com.watermelon.common.model.SubtitleDirection.label(): String = when (this) {
    com.watermelon.common.model.SubtitleDirection.AUTO -> "Auto"
    com.watermelon.common.model.SubtitleDirection.FORCE_RTL -> "Force RTL"
    com.watermelon.common.model.SubtitleDirection.FORCE_LTR -> "Force LTR"
}

/** Row with -/+ steppers for numerical values. */
@Composable
private fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = WatermelonSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = WatermelonTypography.typography.bodyLarge,
            color = WatermelonColors.DarkOnSurface,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onMinus) {
                Text(
                    "-",
                    fontSize = 20.sp,
                    color = WatermelonColors.DarkOnSurface
                )
            }
            Text(
                value,
                style = WatermelonTypography.typography.bodyMedium,
                color = WatermelonColors.DarkOnSurface
            )
            TextButton(onClick = onPlus) {
                Text(
                    "+",
                    fontSize = 20.sp,
                    color = WatermelonColors.DarkOnSurface
                )
            }
        }
    }
}