package com.watermelon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.watermelon.ui.components.WatermelonHeader
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

@Composable
fun FolderVisibilityScreen(
    folders: List<Triple<String, String, Boolean>>,
    onToggle: (path: String, visible: Boolean) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        WatermelonHeader(
            title = "Folder Visibility",
            showBackButton = true,
            onBackClick = onBack,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(WatermelonSpacing.sm))

        Text(
            text = "Hidden folders are excluded from the library index.",
            style = WatermelonTypography.typography.bodySmall,
            color = WatermelonColors.DarkOnSurfaceVariant,
            modifier = Modifier.padding(
                start = WatermelonSpacing.md,
                end = WatermelonSpacing.md,
                bottom = WatermelonSpacing.sm
            )
        )

        val hiddenCount = folders.count { !it.third }
        if (hiddenCount > 0) {
            Text(
                text = "$hiddenCount hidden",
                style = WatermelonTypography.typography.labelMedium,
                color = WatermelonColors.DarkOnSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(
                    start = WatermelonSpacing.md,
                    end = WatermelonSpacing.md,
                    bottom = WatermelonSpacing.md
                )
            )
        }

        HorizontalDivider(
            color = WatermelonColors.DarkOutline,
            thickness = WatermelonSpacing.hairline
        )

        if (folders.isEmpty()) {
            Text(
                text = "No folders indexed yet.",
                style = WatermelonTypography.typography.bodyMedium,
                color = WatermelonColors.DarkOnSurfaceVariant,
                modifier = Modifier.padding(WatermelonSpacing.md)
            )
            return@Column
        }

        val sortedFolders = folders.sortedBy { it.second.lowercase() }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(sortedFolders, key = { it.first }) { (path, displayName, isVisible) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = isVisible,
                            onValueChange = { onToggle(path, it) },
                            role = Role.Switch
                        )
                        .padding(
                            horizontal = WatermelonSpacing.md,
                            vertical = WatermelonSpacing.sm
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = WatermelonSpacing.md)) {
                        Text(
                            text = displayName,
                            style = WatermelonTypography.typography.bodyLarge,
                            color = WatermelonColors.DarkOnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = path,
                            style = WatermelonTypography.typography.labelSmall,
                            color = WatermelonColors.DarkOnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = isVisible,
                        onCheckedChange = null
                    )
                }
                HorizontalDivider(
                    color = WatermelonColors.DarkOutline,
                    thickness = WatermelonSpacing.hairline,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.md)
                )
            }
        }
    }
}