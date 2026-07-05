package com.watermelon.ui.screens

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.Playlist
import com.watermelon.ui.R
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.components.LabeledIconButton
import com.watermelon.ui.components.VelocityGuardImage
import com.watermelon.ui.components.VideoSelectionBar
import com.watermelon.ui.components.WatermelonLoadingAnimation
import com.watermelon.ui.viewmodel.VideoListViewModel
import kotlinx.coroutines.delay

private enum class VideoSort(val label: String) {
    NAME("Name"), DATE("Date"), DURATION("Duration"),
    FILE_TYPE("File Type"), SIZE("Size"), QUALITY("Quality"), CUSTOM("Custom")
}
private enum class VideoLayout { LIST, GRID }

private val LayoutSaver = androidx.compose.runtime.saveable.Saver<VideoLayout, String>(
    save    = { it.name },
    restore = { VideoLayout.valueOf(it) }
)
private val SortSaver2 = androidx.compose.runtime.saveable.Saver<VideoSort, String>(
    save    = { it.name },
    restore = { VideoSort.valueOf(it) }
)
private val SizeSaver2 = androidx.compose.runtime.saveable.Saver<ItemSize, String>(
    save    = { it.name },
    restore = { ItemSize.valueOf(it) }
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VideoListScreen(
    viewModel: VideoListViewModel,
    onVideoClick: (MediaItem) -> Unit,
    onRefresh: () -> Unit = {},
    availablePlaylists: List<Playlist> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context     = LocalContext.current
    val videos      by viewModel.videos.collectAsStateWithLifecycle()
    val selection   by viewModel.selection.collectAsStateWithLifecycle()

    var currentSort     by rememberSaveable(stateSaver = SortSaver2)  { mutableStateOf(VideoSort.NAME) }
    var ascending       by rememberSaveable { mutableStateOf(true) }
    var currentItemSize by rememberSaveable(stateSaver = SizeSaver2)  { mutableStateOf(ItemSize.MEDIUM) }
    var currentLayout   by rememberSaveable(stateSaver = LayoutSaver) { mutableStateOf(VideoLayout.LIST) }
    var sortMenuOpen    by remember { mutableStateOf(false) }
    var isRefreshing    by remember { mutableStateOf(false) }
    var showDeleteDialog     by remember { mutableStateOf(false) }

    // Launcher for the MediaStore delete-consent dialog (scoped storage, API 30+).
    val deleteLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onDeleteConfirmed()
        }
    }
    var showPlaylistPicker   by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) { onRefresh(); delay(2_000); isRefreshing = false }
    }

    val sorted = remember(videos, currentSort, ascending) {
        // CUSTOM preserves the order the source flow already provides (drag-reorder from
        // the repository in Phase A / E5); we don't re-sort it here.
        if (currentSort == VideoSort.CUSTOM) {
            videos
        } else {
            val cmp: Comparator<MediaItem> = when (currentSort) {
                VideoSort.NAME      -> compareBy { it.displayName.lowercase() }
                VideoSort.DATE      -> compareByDescending { if (it.dateAdded > 0L) it.dateAdded else it.firstSeenAt }
                VideoSort.DURATION  -> compareByDescending { it.durationMs }
                VideoSort.FILE_TYPE -> compareBy { it.fileExtension.lowercase() }
                VideoSort.SIZE      -> compareByDescending { it.fileSize }
                VideoSort.QUALITY   -> compareByDescending { it.pixelCount }
                VideoSort.CUSTOM    -> compareBy { 0 } // unreachable
            }
            videos.sortedWith(if (ascending) cmp else Comparator { a, b -> cmp.compare(b, a) })
        }
    }

    val listState   = rememberLazyListState()
    val gridState   = rememberLazyGridState()
    val isGrid      = currentLayout == VideoLayout.GRID
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress || gridState.isScrollInProgress } }

    // Scroll to top whenever the sort or direction changes (issue 5).
    LaunchedEffect(currentSort, ascending) {
        runCatching { listState.scrollToItem(0) }
        runCatching { gridState.scrollToItem(0) }
    }

    val gridColumns = when (currentItemSize) {
        ItemSize.SMALL  -> GridCells.Fixed(3)
        ItemSize.MEDIUM -> GridCells.Fixed(2)
        ItemSize.LARGE  -> GridCells.Fixed(2)
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title   = { Text("Delete ${selection.count} video(s)?") },
            text    = { Text("This will permanently delete the selected files from your device.") },
            confirmButton = {
                TextButton(onClick = {
                    val sender = viewModel.buildDeleteRequest(context.contentResolver)
                    if (sender != null) {
                        deleteLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                        )
                    }
                    showDeleteDialog = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Playlist picker dialog
    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Add to playlist") },
            text  = {
                Column {
                    availablePlaylists.filter {
                        it.type == com.watermelon.common.model.PlaylistType.USER
                    }.forEach { playlist ->
                        TextButton(
                            onClick = {
                                viewModel.addSelectedToPlaylist(playlist.id)
                                showPlaylistPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(playlist.name) }
                    }
                    if (availablePlaylists.none { it.type == com.watermelon.common.model.PlaylistType.USER }) {
                        Text(
                            "No playlists yet. Create one in the folder browser.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (selection.isActive) {
                VideoSelectionBar(
                    selectedCount     = selection.count,
                    onShare = {
                        val intent = viewModel.buildShareIntent()
                        context.startActivity(Intent.createChooser(intent, "Share videos"))
                        viewModel.clearSelection()
                    },
                    onDelete          = { showDeleteDialog = true },
                    onAddToPlaylist   = { showPlaylistPicker = true },
                    onAddToFavourites = { viewModel.addSelectedToFavourites() }
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // ── Toolbar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selection.isActive) {
                    // Selection mode toolbar
                    TextButton(onClick = { viewModel.selectAll() }) { Text("Select all") }
                    TextButton(onClick = { viewModel.clearSelection() }) { Text("Cancel") }
                } else {
                    // Normal toolbar
                    LabeledIconButton(
                        icon    = if (isGrid) WatermelonIcons.ViewList else WatermelonIcons.ViewGrid,
                        label   = if (isGrid) "List" else "Grid",
                        onClick = { currentLayout = if (isGrid) VideoLayout.LIST else VideoLayout.GRID }
                    )
                    Box {
                        LabeledIconButton(
                            icon    = WatermelonIcons.Sort,
                            label   = "Sort: ${currentSort.label}",
                            onClick = { sortMenuOpen = true }
                        )
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            VideoSort.values().forEach { opt ->
                                DropdownMenuItem(
                                    text    = { Text(opt.label) },
                                    onClick = { currentSort = opt; sortMenuOpen = false }
                                )
                            }
                        }
                    }
                    LabeledIconButton(
                        icon    = if (ascending) R.drawable.ic_sort_ascending else R.drawable.ic_sort_descending,
                        label   = if (ascending) "Ascending" else "Descending",
                        onClick = { ascending = !ascending }
                    )
                    ItemSize.values().forEach { size ->
                        LabeledIconButton(
                            icon    = when (size) {
                                ItemSize.SMALL  -> R.drawable.ic_size_small
                                ItemSize.MEDIUM -> R.drawable.ic_size_medium
                                ItemSize.LARGE  -> R.drawable.ic_size_large
                            },
                            label   = size.label,
                            active  = size == currentItemSize,
                            onClick = { currentItemSize = size }
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── Content ───────────────────────────────────────────────────────
            if (sorted.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WatermelonLoadingAnimation(modifier = Modifier.size(160.dp))
                }
                return@Column
            }

            PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { isRefreshing = true }) {
                when (currentLayout) {
                    VideoLayout.LIST -> LazyColumn(
                        state   = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(sorted, key = { it.uri }) { item ->
                            VideoListItem(
                                item            = item,
                                itemSize        = currentItemSize,
                                isGrid          = false,
                                isScrollingFast = isScrolling,
                                isSelected      = selection.contains(item.uri),
                                selectionActive = selection.isActive,
                                onClick         = {
                                    if (selection.isActive) {
                                        viewModel.onToggleSelect(item.uri)
                                    } else {
                                        viewModel.markPlayed(item.uri)
                                        com.watermelon.ui.screens.PlaybackQueue.set(sorted.map { it.uri })
                                        onVideoClick(item)
                                    }
                                },
                                onLongClick     = { viewModel.onLongPress(item.uri) }
                            )
                        }
                    }

                    VideoLayout.GRID -> LazyVerticalGrid(
                        state   = gridState,
                        columns = gridColumns,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement   = Arrangement.spacedBy(8.dp)
                    ) {
                        gridItems(sorted, key = { it.uri }) { item ->
                            VideoListItem(
                                item            = item,
                                itemSize        = currentItemSize,
                                isGrid          = true,
                                isScrollingFast = isScrolling,
                                isSelected      = selection.contains(item.uri),
                                selectionActive = selection.isActive,
                                onClick         = {
                                    if (selection.isActive) {
                                        viewModel.onToggleSelect(item.uri)
                                    } else {
                                        viewModel.markPlayed(item.uri)
                                        com.watermelon.ui.screens.PlaybackQueue.set(sorted.map { it.uri })
                                        onVideoClick(item)
                                    }
                                },
                                onLongClick     = { viewModel.onLongPress(item.uri) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Video item ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoListItem(
    item: MediaItem,
    itemSize: ItemSize,
    isGrid: Boolean,
    isScrollingFast: Boolean,
    isSelected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val thumbH: Dp = when (itemSize) {
        ItemSize.SMALL  -> if (isGrid) 72.dp  else 40.dp
        ItemSize.MEDIUM -> if (isGrid) 120.dp else 64.dp
        ItemSize.LARGE  -> if (isGrid) 180.dp else 96.dp
    }
    val textStyle = when (itemSize) {
        ItemSize.SMALL  -> MaterialTheme.typography.bodySmall
        ItemSize.MEDIUM -> MaterialTheme.typography.bodyMedium
        ItemSize.LARGE  -> MaterialTheme.typography.bodyLarge
    }
    val selectedBorder = if (isSelected)
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
    else Modifier

    val clickModifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .then(selectedBorder)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)

    if (isGrid) {
        Column(
            modifier = clickModifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            VelocityGuardImage(
                uri             = item.uri,
                durationMs      = item.durationMs,
                isScrollingFast = isScrollingFast,
                modifier        = Modifier.fillMaxWidth().height(thumbH).clip(RoundedCornerShape(6.dp))
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text     = item.displayName,
                    style    = textStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.lastPlayedAt == null) {
                    Icon(
                        painterResource(R.drawable.ic_badge_new),
                        contentDescription = "New",
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    } else {
        Row(
            modifier = clickModifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            VelocityGuardImage(
                uri             = item.uri,
                durationMs      = item.durationMs,
                isScrollingFast = isScrollingFast,
                modifier        = Modifier
                    .width(thumbH * 16f / 9f)
                    .height(thumbH)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text     = item.displayName,
                        style    = textStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.lastPlayedAt == null) {
                        Icon(
                            painterResource(R.drawable.ic_badge_new),
                            contentDescription = "New",
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Text(
                    text  = formatDuration(item.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}