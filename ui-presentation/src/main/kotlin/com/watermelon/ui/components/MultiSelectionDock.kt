package com.watermelon.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.watermelon.ui.R
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

/**
 * Multi-selection dock for batch operations on videos.
 * Implements the "Multi-selection dock" requirement from the UI Design System.
 */
@Composable
fun MultiSelectionDock(
    selectedCount: Int,
    onDeselectAll: () -> Unit,
    onDelete: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = false
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(WatermelonColors.DarkSurface)
                .border(
                    width = 1.dp,
                    color = WatermelonColors.DarkOutline,
                    shape = WatermelonShapes.sharp
                )
                .padding(WatermelonSpacing.md),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Selection count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_circle),
                        contentDescription = "Selected",
                        tint = WatermelonColors.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(WatermelonSpacing.sm))
                    Text(
                        text = "$selectedCount selected",
                        style = WatermelonTypography.typography.labelLarge,
                        color = WatermelonColors.DarkOnSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // Deselect all
                    IconButton(
                        onClick = onDeselectAll,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Deselect all",
                            tint = WatermelonColors.DarkOnSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(WatermelonSpacing.xs))

                    // Add to playlist
                    IconButton(
                        onClick = onAddToPlaylist,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_playlist_add),
                            contentDescription = "Add to playlist",
                            tint = WatermelonColors.DarkOnSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(WatermelonSpacing.xs))

                    // Share
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_share),
                            contentDescription = "Share",
                            tint = WatermelonColors.DarkOnSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(WatermelonSpacing.xs))

                    // Delete
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = "Delete",
                            tint = WatermelonColors.Error
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun MultiSelectionDockPreview() {
    MultiSelectionDock(
        selectedCount = 3,
        onDeselectAll = {},
        onDelete = {},
        onAddToPlaylist = {},
        onShare = {},
        visible = true
    )
}