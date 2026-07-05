package com.watermelon.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.watermelon.ui.R
import com.watermelon.ui.WatermelonIcons

/**
 * Bottom action bar shown when one or more videos are selected.
 * Actions: Share, Delete, Add to Playlist, Add to Favourites.
 */
@Composable
fun VideoSelectionBar(
    selectedCount: Int,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToFavourites: () -> Unit,
    modifier: Modifier = Modifier
) {
    BottomAppBar(
        modifier = modifier.height(64.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text  = "$selectedCount selected",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LabeledIconButton(
                    icon    = WatermelonIcons.Favorite,
                    label   = "Favourite",
                    tint    = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onAddToFavourites
                )
                LabeledIconButton(
                    icon    = WatermelonIcons.PlaylistAdd,
                    label   = "Playlist",
                    tint    = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onAddToPlaylist
                )
                LabeledIconButton(
                    icon    = WatermelonIcons.Share,
                    label   = "Share",
                    tint    = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onShare
                )
                LabeledIconButton(
                    icon    = WatermelonIcons.Delete,
                    label   = "Delete",
                    tint    = MaterialTheme.colorScheme.error,
                    onClick = onDelete
                )
            }
        }
    }
}
