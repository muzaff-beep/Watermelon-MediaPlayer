package com.watermelon.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.Playlist
import com.watermelon.ui.components.WatermelonHeader
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography
import com.watermelon.ui.viewmodel.PlaylistViewModel

/**
 * Lists all playlists (system: Recently Added, Favourites, Continue Watching,
 * plus user-created ones). Tapping one opens its videos via the existing
 * `videos/{folderPath}?isPlaylist=true` route.
 */
@Composable
fun PlaylistsScreen(
    viewModel: PlaylistViewModel,
    onPlaylistClick: (Playlist) -> Unit
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        WatermelonHeader(title = "Playlists")

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No playlists yet",
                    style = WatermelonTypography.typography.bodyLarge,
                    color = WatermelonColors.DarkOnSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(playlists, key = { it.id }) { playlist ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaylistClick(playlist) }
                            .padding(horizontal = WatermelonSpacing.md, vertical = 14.dp)
                    ) {
                        Text(
                            text = playlist.name,
                            style = WatermelonTypography.typography.bodyLarge,
                            color = WatermelonColors.DarkOnSurface
                        )
                        Text(
                            text = "${playlist.itemCount} video${if (playlist.itemCount == 1) "" else "s"}",
                            style = WatermelonTypography.typography.bodySmall,
                            color = WatermelonColors.DarkOnSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = WatermelonColors.DarkSurfaceVariant)
                }
            }
        }
    }
}
