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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
                horizontal = WatermelonSpacing.md,
                vertical = 0.dp
            ),
            verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.md)
        ) {
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

            item {
                SettingsGroup("PLAYER") {
                    ToggleRow(
                        label = "Burst screenshot (9 frames)",
                        checked = state.screenshotMode == ScreenshotMode.BURST
                    ) {
                        onStateChange(
                            state.copy(screenshotMode = if (it) ScreenshotMode.BURST else ScreenshotMode.SINGLE)