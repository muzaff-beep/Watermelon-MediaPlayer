package com.watermelon.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.SwitchDefaults
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
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

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
                horizontal = WatermelonSpacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.md)
        ) {
            item {
                SettingsGroup(title = "APPEARANCE") {
                    ToggleRow(
                        label = "Pure dark theme",
                        checked = state.pureDark
                    ) { onStateChange(state.copy(pureDark = it)) }

                    ToggleRow(
                        label = "Force RTL overrides",
                        checked = state.forcedRtl
                    ) { onStateChange(state.copy(forcedRtl = it)) }
                }
            }

            item {
                SettingsGroup(title = "VIEW DEFAULTS") {
                    ToggleRow(
                        label = "Grid layout by default",
                        checked = state.gridDefault
                    ) { onStateChange(state.copy(gridDefault = it)) }

                    ToggleRow(
                        label = "Show thumbnails",
                        checked = state.showThumbnails
                    ) { onStateChange(state.copy(showThumbnails = it)) }

                    ToggleRow(
                        label = "Show durations",
                        checked = state.showDurations
                    ) { onStateChange(state.copy(showDurations = it)) }

                    ToggleRow(
                        label = "Show file size",
                        checked = state.showFileSize
                    ) { onStateChange(state.copy(showFileSize = it)) }
                }
            }

            item {
                SettingsGroup(title = "PLAYER") {
                    ToggleRow(
                        label = "Burst screenshot (9 frames)",
                        checked = state.screenshotMode == ScreenshotMode.BURST
                    ) {
                        onStateChange(
                            state.copy(screenshotMode = if (it) ScreenshotMode.BURST else ScreenshotMode.SINGLE)
                        )
                    }

                    ToggleRow(
                        label = "VHS effect",
                        checked = state.vhsEnabled
                    ) { onStateChange(state.copy(vhsEnabled = it)) }

                    DropdownNavRow(
                        label = "VHS intensity",
                        value = state.vhsIntensity.name.lowercase().replaceFirstChar { it.uppercase() },
                        options = VhsIntensity.values().map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                    ) { selected ->
                        val next = VhsIntensity.values().first {
                            it.name.lowercase().replaceFirstChar { c -> c.uppercase() } == selected
                        }
                        onStateChange(state.copy(vhsIntensity = next))
                    }
                }
            }

            item {
                SettingsGroup(title = "SUBTITLES") {
                    val st = state.subtitleStyle
                    fun up(new: com.watermelon.common.model.SubtitleStyle) {
                        onStateChange(state.copy(subtitleStyle = new))
                    }

                    ToggleRow(
                        label = "Enable subtitles",
                        checked = st.enabled
                    ) { up(st.copy(enabled = it)) }

                    StepperRow(
                        label = "Text size",
                        value = "${st.sizeSp}sp",
                        onMinus = { up(st.copy(sizeSp = (st.sizeSp - 2).coerceAtLeast(12))) },
                        onPlus = { up(st.copy(sizeSp = (st.sizeSp + 2).coerceAtMost(48))) }
                    )

                    DropdownNavRow(
                        label = "Text color",
                        value = subtitleColorName(st.textColorArgb),
                        options = SUBTITLE_COLORS.map { it.second }
                    ) { selected ->
                        val argb = SUBTITLE_COLORS.first { it.second == selected }.first
                        up(st.copy(textColorArgb = argb))
                    }

                    DropdownNavRow(
                        label = "Position",
                        value = st.position.name.lowercase().replaceFirstChar { it.uppercase() },
                        options = com.watermelon.common.model.SubtitlePosition.values()
                            .map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                    ) { selected ->
                        val next = com.watermelon.common.model.SubtitlePosition.values().first {
                            it.name.lowercase().replaceFirstChar { c -> c.uppercase() } == selected
                        }
                        up(st.copy(position = next))
                    }

                    ToggleRow(label = "Bold", checked = st.bold) { up(st.copy(bold = it)) }
                    ToggleRow(label = "Italic", checked = st.italic) { up(st.copy(italic = it)) }
                    ToggleRow(label = "Underline", checked = st.underline) { up(st.copy(underline = it)) }

                    DropdownNavRow(
                        label = "Direction",
                        value = st.direction.label(),
                        options = com.watermelon.common.model.SubtitleDirection.values().map { it.label() }
                    ) { selected ->
                        val next = com.watermelon.common.model.SubtitleDirection.values().first { it.label() == selected }
                        up(st.copy(direction = next))
                    }

                    DropdownNavRow(
                        label = "2nd sub direction",
                        value = st.secondaryDirection.label(),
                        options = com.watermelon.common.model.SubtitleDirection.values().map { it.label() }
                    ) { selected ->
                        val next = com.watermelon.common.model.SubtitleDirection.values().first { it.label() == selected }
                        up(st.copy(secondaryDirection = next))
                    }
                }
            }

            item {
                SettingsGroup(title = "SYSTEM") {
                    ToggleRow(
                        label = "Memory-safety (force Tier B)",
                        checked = state.memorySafety
                    ) { onStateChange(state.copy(memorySafety = it)) }

                    ToggleRow(
                        label = "Full folder access (power-user)",
                        checked = state.fullFolderAccess
                    ) { onStateChange(state.copy(fullFolderAccess = it)) }

                    NavRow(
                        label = "Folder visibility",
                        value = "Manage",
                        onClick = onFolderVisibilityClick
                    )
                }
            }
        }
    }
}

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
            shape = WatermelonShapes.control,
            color = WatermelonColors.DarkSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = WatermelonSpacing.md,
                    vertical = WatermelonSpacing.xs
                )
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
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
            text = label,
            style = WatermelonTypography.typography.bodyLarge,
            color = WatermelonColors.DarkOnSurface,
            modifier = Modifier.weight(1f).padding(end = WatermelonSpacing.md)
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = WatermelonColors.Accent,
                checkedTrackColor = WatermelonColors.Accent.copy(alpha = 0.5f),
                uncheckedThumbColor = WatermelonColors.DarkOnSurfaceVariant,
                uncheckedTrackColor = WatermelonColors.DarkSurface
            )
        )
    }
}

@Composable
private fun DropdownNavRow(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
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
                text = label,
                style = WatermelonTypography.typography.bodyLarge,
                color = WatermelonColors.DarkOnSurface,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = WatermelonTypography.typography.bodyMedium,
                    color = WatermelonColors.DarkOnSurfaceVariant
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
                    text = {
                        Text(
                            text = option,
                            style = WatermelonTypography.typography.bodyMedium,
                            color = WatermelonColors.DarkOnSurface
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun NavRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = WatermelonSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = WatermelonTypography.typography.bodyLarge,
            color = WatermelonColors.DarkOnSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$value >",
            style = WatermelonTypography.typography.bodyMedium,
            color = WatermelonColors.DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = WatermelonSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = WatermelonTypography.typography.bodyLarge,
            color = WatermelonColors.DarkOnSurface,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onMinus) {
                Text(
                    text = "-",
                    fontSize = 20.sp,
                    color = WatermelonColors.DarkOnSurface
                )
            }
            Text(
                text = value,
                style = WatermelonTypography.typography.bodyMedium,
                color = WatermelonColors.DarkOnSurface
            )
            TextButton(onClick = onPlus) {
                Text(
                    text = "+",
                    fontSize = 20.sp,
                    color = WatermelonColors.DarkOnSurface
                )
            }
        }
    }
}

private val SUBTITLE_COLORS = listOf(
    0xFFFFFFFFL to "White",
    0xFFFFEB3BL to "Yellow",
    0xFF00E5FFL to "Cyan",
    0xFF69F0AEL to "Green",
    0xFFFF8A80L to "Coral",
    0xFF000000L to "Black"
)

private fun subtitleColorName(argb: Long): String =
    SUBTITLE_COLORS.firstOrNull { it.first == argb }?.second ?: "Custom"

private fun com.watermelon.common.model.SubtitleDirection.label(): String = when (this) {
    com.watermelon.common.model.SubtitleDirection.AUTO -> "Auto"
    com.watermelon.common.model.SubtitleDirection.FORCE_RTL -> "Force RTL"
    com.watermelon.common.model.SubtitleDirection.FORCE_LTR -> "Force LTR"
}