package com.watermelon.ui.tv

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.Playlist
import com.watermelon.common.model.PlaylistType
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.viewmodel.PlaylistViewModel

/**
 * D-Pad-optimised playlists screen for Android TV, matching [TvFolderBrowserScreen]'s
 * row-list-with-focus-ring pattern. Reuses the existing headless [PlaylistViewModel] — no
 * TV-specific view-model logic needed, same as the phone screen.
 *
 * Create/rename use a standard [AlertDialog] + [TextField]: this relies on the default Android
 * on-screen keyboard for D-pad text entry, which is left as-is for this pass rather than a
 * custom D-pad-friendly input flow — the dialog's OK/Cancel buttons and the field itself are
 * still reachable and operable via D-pad focus navigation, but actual on-screen-keyboard
 * behavior needs manual verification on a real remote/emulator; this is not assumed to just
 * work out of the box the way it does with a touchscreen or a physical keyboard.
 *
 * System playlists (Recently Added, Favourites, Continue Watching) have no rename/delete
 * action, matching [Playlist]'s own doc comment and the phone screen's identical rule.
 *
 * A user-playlist row is itself focusable/clickable (opens the playlist) *and* contains two
 * further focusable Rename/Delete buttons — three separate D-pad stops per row rather than one
 * row plus a hidden long-press/overflow menu, since a D-pad has no long-press affordance and an
 * overflow menu is an extra navigation hop for two actions that are common enough to warrant
 * being always visible.
 */
@Composable
fun TvPlaylistsScreen(
    viewModel: PlaylistViewModel,
    onPlaylistClick: (Playlist) -> Unit,
    onSettingsClick: () -> Unit,
    continueWatchingEnabled: Boolean = true,
    modifier: Modifier = Modifier
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

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Playlists",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = WatermelonSpacing.xl + WatermelonSpacing.md,
                    vertical = WatermelonSpacing.sm
                )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = WatermelonSpacing.xl + WatermelonSpacing.md,
                    vertical = WatermelonSpacing.xs
                ),
            verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
        ) {
            item(key = "tv_settings_entry") {
                TvFocusableRow(
                    onClick = onSettingsClick,
                    primaryText = "Settings"
                )
            }
            item(key = "tv_new_playlist_entry") {
                TvFocusableRow(
                    onClick = { showCreateDialog = true },
                    primaryText = "+ New playlist"
                )
            }
            items(playlists, key = { it.id }) { playlist ->
                val isUserPlaylist = playlist.type == PlaylistType.USER
                TvFocusableRow(
                    onClick = { onPlaylistClick(playlist) },
                    primaryText = playlist.name,
                    secondaryText = "${playlist.itemCount} video${if (playlist.itemCount == 1) "" else "s"}",
                    trailingActions = if (isUserPlaylist) {
                        {
                            TextButton(onClick = { renameTarget = playlist }) {
                                Text("Rename", color = MaterialTheme.colorScheme.secondary)
                            }
                            TextButton(onClick = { deleteTarget = playlist }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else null
                )
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New playlist") },
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
                TextButton(onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        viewModel.createPlaylist(trimmed)
                        showCreateDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    renameTarget?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename playlist") },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        viewModel.renamePlaylist(target.id, trimmed)
                        renameTarget = null
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = { Text("This removes the playlist. The video files themselves are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(target.id)
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Shared focusable row for TV list screens outside [TvFolderBrowserScreen] (which builds its
 * own inline). Matches the same visual language: [WatermelonShapes.card] shape,
 * `colorScheme.secondary` focus ring — see [TvFolderBrowserScreen]'s doc comment for why Teal
 * (not Red) is the dedicated D-pad focus color across the TV surface.
 */
@Composable
private fun TvFocusableRow(
    onClick: () -> Unit,
    primaryText: String,
    secondaryText: String? = null,
    trailingActions: (@Composable () -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = WatermelonShapes.card,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.secondary else Color.Transparent,
                shape = WatermelonShapes.card
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WatermelonSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (secondaryText != null) {
                    Text(
                        text = secondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailingActions?.invoke()
        }
    }
}
