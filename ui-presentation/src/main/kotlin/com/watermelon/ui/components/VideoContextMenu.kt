package com.watermelon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.watermelon.ui.R
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

/**
 * Context menu for video items.
 */
@Composable
fun VideoContextMenu(
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToFavorites: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        androidx.compose.material3.IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vertical),
                contentDescription = "More options",
                tint = WatermelonColors.DarkOnSurface
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(WatermelonColors.DarkSurface, WatermelonShapes.control)
                .clip(WatermelonShapes.control)
        ) {
            ContextMenuItem(
                iconRes = R.drawable.ic_play_arrow,
                text = "Play",
                onClick = { onPlay(); expanded = false }
            )

            ContextMenuItem(
                iconRes = R.drawable.ic_queue_next,
                text = "Play next",
                onClick = { onPlayNext(); expanded = false }
            )

            ContextMenuItem(
                iconRes = R.drawable.ic_playlist_add,
                text = "Add to playlist",
                onClick = { onAddToPlaylist(); expanded = false }
            )

            ContextMenuItem(
                iconRes = R.drawable.ic_star_off,
                text = "Add to favorites",
                onClick = { onAddToFavorites(); expanded = false }
            )

            ContextMenuItem(
                iconRes = R.drawable.ic_share,
                text = "Share",
                onClick = { onShare(); expanded = false }
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Delete",
                        style = WatermelonTypography.typography.labelMedium,
                        color = WatermelonColors.Error
                    )
                },
                onClick = { onDelete(); expanded = false },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = "Delete",
                        tint = WatermelonColors.Error
                    )
                }
            )

            ContextMenuItem(
                iconRes = R.drawable.ic_edit,
                text = "Rename",
                onClick = { onRename(); expanded = false }
            )

            ContextMenuItem(
                iconRes = R.drawable.ic_info,
                text = "Properties",
                onClick = { onProperties(); expanded = false }
            )
        }
    }
}

@Composable
private fun ContextMenuItem(
    iconRes: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = WatermelonTypography.typography.labelMedium,
                color = WatermelonColors.DarkOnSurface
            )
        },
        onClick = onClick,
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = text,
                tint = WatermelonColors.DarkOnSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        modifier = modifier
    )
}