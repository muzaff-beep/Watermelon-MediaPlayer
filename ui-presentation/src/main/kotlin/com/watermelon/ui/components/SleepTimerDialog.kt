package com.watermelon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
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
 * @param isRunning whether a custom countdown timer is currently active
 * @param remainingMs remaining time in ms for the active countdown (only meaningful when [isRunning])
 * @param onDismiss called when user closes without selecting
 * @param onSetTimer called with (mode, minutesOrNull) where:
 *   - mode: "current_video" | "folder" | "custom"
 *   - minutesOrNull: Int for custom mode, null for others
 * @param onCancelTimer called when the user cancels the currently-active timer
 */
@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onSetTimer: (mode: String, minutes: Int?) -> Unit,
    isRunning: Boolean = false,
    remainingMs: Long = 0L,
    onCancelTimer: () -> Unit = {}
) {
    var selectedMode by remember { mutableStateOf<String?>(null) }
    var customMinutes by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf<Int?>(null) }

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

                // Active-timer banner — only shown while a custom countdown is running.
                if (isRunning) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Timer running — ${formatRemaining(remainingMs)} left",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { onCancelTimer(); onDismiss() }) {
                                Text("Cancel timer")
                            }
                        }
                    }
                }

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
                                val isSelected = selectedPreset == minutes
                                Button(
                                    onClick = {
                                        customMinutes = minutes.toString()
                                        selectedPreset = minutes
                                    },
                                    colors = if (isSelected) {
                                        androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("${minutes}m", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                                }
                            }
                        }

                        // Custom input
                        TextField(
                            value = customMinutes,
                            onValueChange = {
                                customMinutes = it.filter { c -> c.isDigit() }
                                selectedPreset = null
                            },
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
    // Selected state uses a bordered surface rather than filled accent-on-white/black —
    // primary (Watermelon Red) doesn't meet AA text contrast against onPrimary in either
    // theme, so the accent color is reserved for the border/indicator, not body text.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .then(
                if (isSelected)
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatRemaining(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
