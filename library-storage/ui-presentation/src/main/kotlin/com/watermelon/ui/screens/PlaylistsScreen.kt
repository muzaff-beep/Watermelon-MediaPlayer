package com.watermelon.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.Playlist
import com.watermelon.common.model.PlaylistType
import com.watermelon.ui.R
import com.watermelon.ui.components.WatermelonHeader
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography
import com.watermelon.ui.viewmodel.PlaylistViewModel

/**
 * Lists all playlists (system: Continue Watching, Recently Added, Favourites,
 * plus user-created ones) and provides full management for user playlists:
 * create, rename, delete. Tapping a row opens its videos via the existing
 * `videos/{folderPath}?isPlaylist=true` route, where items can be added/removed
 * (see VideoListScreen's MultiSelectionDock "Add to playlist" / "Remove from playlist").
 *
 * System playlists (Recently Added, Favourites, Continue Watching) have no rename/delete
 * menu — they're computed or have their own dedicated add/remove mechanism, matching
 * Playlist's own doc comment ("System playlists have fixed ids and cannot be renamed or
 * deleted").
 *
 * [continueWatchingEnabled] hides the Continue Watching row entirely when the user has
 * turned it off in Settings — resume-position tracking itself keeps running in the
 * background regardless (that's handled at the PlaybackController level, not here); this
 * screen only controls whether the entry point is visible.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(
    viewModel: PlaylistViewModel,
    onPlaylistClick: (Playlist) -> Unit,
    continueWatchingEnabled: Boolean = true
) {
    val allPlaylists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlists = remember(allPlaylists, continueWatchingEnabled) {
        if (continueWatchingEnabled) {
            allPlaylists
        } else {
            allPlaylists.filter { it.type != PlaylistType.CONTINUE_WATCHING }
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }
    var menuTarget by remember { mutableStateOf<Playlist?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        WatermelonHeader(title = "Playlists")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showCreateDialog = true }
                .padding(horizontal = WatermelonSpacing.md, vertical = WatermelonSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_playlist_add),
                contentDescription = null,
                tint = WatermelonColors.Accent,
                modifier = Modifier.size(20.dp)
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(WatermelonSpacing.sm))
            Text(
                "New playlist",
                style = WatermelonTypography.typography.bodyLarge,
                color = WatermelonColors.Accent
            )
        }
        HorizontalDivider(color = WatermelonColors.DarkSurfaceVariant)

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
                    val isUserPlaylist = playlist.type == PlaylistType.USER

                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onPlaylistClick(playlist) },
                                    onLongClick = { if (isUserPlaylist) menuTarget = playlist }
                                )
                                .padding(horizontal = WatermelonSpacing.md, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
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
                            // System playlists: no overflow menu at all (nothing to
                            // rename/delete). User playlists: overflow menu as an explicit
                            // tap target, in addition to long-press, since long-press alone
                            // is easy to miss as a discoverable affordance.
                            if (isUserPlaylist) {
                                IconButton(onClick = { menuTarget = playlist }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_more_vertical),
                                        contentDescription = "Playlist options",
                                        tint = WatermelonColors.DarkOnSurfaceVariant
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = menuTarget?.id == playlist.id,
                            onDismissRequest = { menuTarget = null }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    renameTarget = playlist
                                    menuTarget = null
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = WatermelonColors.Error) },
                                onClick = {
                                    deleteTarget = playlist
                                    menuTarget = null
                                }
                            )
                        }
                    }
                    HorizontalDivider(color = WatermelonColors.DarkSurfaceVariant)
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New playlist", color = WatermelonColors.DarkOnSurface) },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.createPlaylist(trimmed)
                            showCreateDialog = false
                        }
                    }
                ) { Text("Create", color = WatermelonColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = WatermelonColors.DarkOnSurface)
                }
            }
        )
    }

    renameTarget?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename playlist", color = WatermelonColors.DarkOnSurface) },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.renamePlaylist(target.id, trimmed)
                            renameTarget = null
                        }
                    }
                ) { Text("Save", color = WatermelonColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = WatermelonColors.DarkOnSurface)
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${target.name}\"?", color = WatermelonColors.DarkOnSurface) },
            text = {
                Text(
                    "This removes the playlist. The video files themselves are not deleted.",
                    color = WatermelonColors.DarkOnSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist(target.id)
                        deleteTarget = null
                    }
                ) { Text("Delete", color = WatermelonColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = WatermelonColors.DarkOnSurface)
                }
            }
        )
    }
}
