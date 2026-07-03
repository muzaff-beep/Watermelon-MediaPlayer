package com.watermelon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Settings sub-screen for folder visibility.
 * Each folder has a toggle — off means excluded from the index and invisible in
 * FolderBrowserScreen. Toggled state is persisted by the caller via [onToggle].
 *
 * @param folders list of (folderPath, displayName, isVisible)
 * @param onToggle called with (folderPath, newVisibility)
 */
@Composable
fun FolderVisibilityScreen(
    folders: List<Triple<String, String, Boolean>>,
    onToggle: (path: String, visible: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text     = "Folder Visibility",
            style    = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        Text(
            text     = "Hidden folders are excluded from the library index.",
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
        )
        val hiddenCount = folders.count { !it.third }
        if (hiddenCount > 0) {
            Text(
                text     = "$hiddenCount hidden",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )
        }
        HorizontalDivider()

        if (folders.isEmpty()) {
            Text(
                text     = "No folders indexed yet.",
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            return@Column
        }

        val sortedFolders = folders.sortedBy { it.second.lowercase() }
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(sortedFolders, key = { it.first }) { (path, displayName, isVisible) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text     = displayName,
                            style    = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text     = path,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked         = isVisible,
                        onCheckedChange = { onToggle(path, it) }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}