package com.watermelon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Sleep timer configuration dialog. User chooses one of three modes:
 * 1. End of current video
 * 2. End of folder
 * 3. Custom time (5/15/30 min presets or user input)
 *
 * @param onDismiss called when user closes without selecting
 * @param onSetTimer called with (mode, minutesOrNull) where:
 *   - mode: "current_video" | "folder" | "custom"
 *   - minutesOrNull: Int for custom mode, null for others
 */
@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onSetTimer: (mode: String, minutes: Int?) -> Unit
) {
    var selectedMode by remember { mutableStateOf<String?>(null) }
    var customMinutes by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Sleep Timer",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Mode 1: End of current video
                ModeButton(
                    label = "End of current video",
                    isSelected = selectedMode == "current_video",
                    onClick = { selectedMode = "current_video"; customMinutes = "" }
                )

                // Mode 2: End of folder
                ModeButton(
                    label = "End of folder",
                    isSelected = selectedMode == "folder",
                    onClick = { selectedMode = "folder"; customMinutes = "" }
                )

                // Mode 3: Custom time
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ModeButton(
                        label = "Custom time",
                        isSelected = selectedMode == "custom",
                        onClick = { selectedMode = "custom" }
                    )

                    if (selectedMode == "custom") {
                        // Preset buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(5, 15, 30, 60).forEach { minutes ->
                                Button(
                                    onClick = { customMinutes = minutes.toString() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("${minutes}m", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                                }
                            }
                        }

                        // Custom input
                        TextField(
                            value = customMinutes,
                            onValueChange = { customMinutes = it.filter { c -> c.isDigit() } },
                            label = { Text("Minutes") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            when (selectedMode) {
                                "current_video" -> onSetTimer("current_video", null)
                                "folder" -> onSetTimer("folder", null)
                                "custom" -> {
                                    val minutes = customMinutes.toIntOrNull()
                                    if (minutes != null && minutes > 0) {
                                        onSetTimer("custom", minutes)
                                        onDismiss()
                                    }
                                }
                            }
                        },
                        enabled = selectedMode != null && (selectedMode != "custom" || customMinutes.toIntOrNull() != null),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Set")
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}
