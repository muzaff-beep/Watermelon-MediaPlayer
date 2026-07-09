package com.watermelon.ui.screens

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.Playlist
import com.watermelon.ui.R
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.components.LabeledIconButton
import com.watermelon.ui.components.MultiSelectionDock
import com.watermelon.ui.components.StatusBadge
import com.watermelon.ui.components.VideoListItem
import com.watermelon.ui.components.VelocityGuardImage
import com.watermelon.ui.components.WatermelonHeader
import com.watermelon.ui.components.WatermelonLoadingAnimation
import com.watermelon.ui.components.ItemSize
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography
import com.watermelon.ui.viewmodel.VideoListViewModel

private enum class VideoSort(val label: String) {
    NAME("Name"), DATE("Date"), DURATION("Duration"),
    FILE_TYPE("File Type"), SIZE("Size"), QUALITY("Quality"), CUSTOM("Custom")
}

private enum class VideoLayout { LIST, GRID }

private val LayoutSaver = androidx.compose.runtime.saveable.Saver<VideoLayout, String>(
    save = { it.name },
    restore = { VideoLayout.valueOf(it) }
)

private val SortSaver = androidx.compose.runtime.saveable.Saver<VideoSort, String>(
    save = { it.name },
    restore = { VideoSort.valueOf(it) }
)

private val ItemSizeSaver = androidx.compose.runtime.saveable.Saver<ItemSize, String>(
    save = { it.name },
    restore = { ItemSize.valueOf(it) }
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    viewModel: VideoListViewModel,
    onVideoClick: (MediaItem) -> Unit,
    onRefresh: () -> Unit = {},
    availablePlaylists: List<Playlist> = emptyList(),
    folderName: String = "Videos",
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()

    var currentSort by rememberSaveable(stateSaver = SortSaver) { mutableStateOf(VideoSort.NAME) }
    var ascending by rememberSaveable { mutableStateOf(true) }
    var currentItemSize by rememberSaveable(stateSaver = ItemSizeSaver) {
        mutableStateOf(ItemSize.MEDIUM)
    }
    var currentLayout by rememberSaveable(stateSaver = LayoutSaver) {
        mutableStateOf(VideoLayout.LIST)
    }
    val isGrid = currentLayout == VideoLayout.GRID
    var sortMenuOpen by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<String>()) }
    var contextMenuItem by remember { mutableStateOf<MediaItem?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }

    val deleteLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onDeleteConfirmed()
        }
    }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            onRefresh()
            kotlinx.coroutines.delay(2000)
            isRefreshing = false
        }
    }

    val sorted = remember(videos, currentSort, ascending) {
        if (currentSort == VideoSort.CUSTOM) {
            videos
        } else {
            val cmp: Comparator<MediaItem> = when (currentSort) {
                VideoSort.NAME -> compareBy { it.displayName.lowercase() }
                VideoSort.DATE -> compareByDescending {
                    if (it.dateAdded > 0L) it.dateAdded else it.firstSeenAt
                }
                VideoSort.DURATION -> compareByDescending { it.durationMs }
                VideoSort.FILE_TYPE -> compareBy { it.fileExtension.lowercase() }
                VideoSort.SIZE -> compareByDescending { it.fileSize }
                VideoSort.QUALITY -> compareByDescending { it.pixelCount }
                VideoSort.CUSTOM -> compareBy { 0 }
            }
            videos.sortedWith(if (ascending) cmp else Comparator { a, b -> cmp.compare(b, a) })
        }
    }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val isScrolling by remember {
        derivedStateOf { listState.isScrollInProgress || gridState.isScrollInProgress }
    }

    LaunchedEffect(currentSort, ascending) {
        runCatching { listState.scrollToItem(0) }
        runCatching { gridState.scrollToItem(0) }
    }

    val gridColumns = when (currentItemSize) {
        ItemSize.SMALL -> GridCells.Fixed(3)
        ItemSize.MEDIUM -> GridCells.Fixed(2)
        ItemSize.LARGE -> GridCells.Fixed(2)
    }

    Column(modifier = modifier.fillMaxSize()) {
        WatermelonHeader(
            title = folderName,
            showBackButton = true,
            onBackClick = onBack,
            modifier = Modifier.fillMaxWidth()
        )

        if (isMultiSelectMode) {
            MultiSelectionDock(
                selectedCount = selectedItems.size,
                onDeselectAll = {
                    selectedItems = emptySet()
                    isMultiSelectMode = false
                },
                onDelete = { showDeleteDialog = true },
                onAddToPlaylist = { showPlaylistPicker = true },
                onShare = {
                    val intent = viewModel.buildShareIntent()
                    context.startActivity(Intent.createChooser(intent, "Share videos"))
                },
                visible = isMultiSelectMode,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = WatermelonSpacing.sm, vertical = WatermelonSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selection.isActive && !isMultiSelectMode) {
                    TextButton(onClick = { viewModel.selectAll() }) {
                        Text("Select all", color = WatermelonColors.DarkOnSurface)
                    }
                    TextButton(onClick = {
                        viewModel.clearSelection()
                        isMultiSelectMode = false
                        selectedItems = emptySet()
                    }) {
                        Text("Cancel", color = WatermelonColors.DarkOnSurface)
                    }
                } else if (!isMultiSelectMode) {
                    LabeledIconButton(
                        icon = if (isGrid) WatermelonIcons.ViewList else WatermelonIcons.ViewGrid,
                        label = if (isGrid) "List" else "Grid",
                        onClick = { currentLayout = if (isGrid) VideoLayout.LIST else VideoLayout.GRID }
                    )
                    Box {
                        LabeledIconButton(
                            icon = WatermelonIcons.Sort,
                            label = "Sort: ${currentSort.label}",
                            onClick = { sortMenuOpen = true }
                        )
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            VideoSort.values().forEach { opt ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            opt.label,
                                            style = WatermelonTypography.typography.bodyMedium,
                                            color = WatermelonColors.DarkOnSurface
                                        )
                                    },
                                    onClick = { currentSort = opt; sortMenuOpen = false }
                                )
                            }
                        }
                    }
                    LabeledIconButton(
                        icon = if (ascending) R.drawable.ic_sort_ascending else R.drawable.ic_sort_descending,
                        label = if (ascending) "Ascending" else "Descending",
                        onClick = { ascending = !ascending }
                    )
                    ItemSize.values().forEach { size ->
                        LabeledIconButton(
                            icon = when (size) {
                                ItemSize.SMALL -> R.drawable.ic_size_small
                                ItemSize.MEDIUM -> R.drawable.ic_size_medium
                                ItemSize.LARGE -> R.drawable.ic_size_large
                            },
                            label = size.label,
                            active = size == currentItemSize,
                            onClick = { currentItemSize = size }
                        )
                    }
                }
            }

            HorizontalDivider(
                thickness = WatermelonSpacing.hairline,
                color = WatermelonColors.DarkOutline
            )

            if (sorted.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WatermelonLoadingAnimation(modifier = Modifier.size(160.dp))
                }
                return@Column
            }

            PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { isRefreshing = true }) {
                when (currentLayout) {
                    VideoLayout.LIST -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = WatermelonSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs / 2)
                    ) {
                        items(sorted, key = { it.uri }) { item ->
                            val isSelected = if (isMultiSelectMode) {
                                item.uri in selectedItems
                            } else {
                                selection.contains(item.uri)
                            }

                            VideoListItem(
                                item = item,
                                itemSize = currentItemSize,
                                isGrid = false,
                                isScrollingFast = isScrolling,
                                isSelected = isSelected,
                                selectionActive = selection.isActive || isMultiSelectMode,
                                onClick = {
                                    if (isMultiSelectMode) {
                                        selectedItems = if (item.uri in selectedItems) {
                                            selectedItems - item.uri
                                        } else {
                                            selectedItems + item.uri
                                        }
                                    } else if (selection.isActive) {
                                        viewModel.onToggleSelect(item.uri)
                                    } else {
                                        viewModel.markPlayed(item.uri)
                                        PlaybackQueue.set(sorted.map { it.uri })
                                        onVideoClick(item)
                                    }
                                },
                                onLongClick = {
                                    if (!isMultiSelectMode && !selection.isActive) {
                                        isMultiSelectMode = true
                                        selectedItems = setOf(item.uri)
                                    } else {
                                        viewModel.onLongPress(item.uri)
                                    }
                                },
                                onContextMenuClick = {
                                    contextMenuItem = item
                                    showContextMenu = true
                                }
                            )
                        }
                    }

                    VideoLayout.GRID -> LazyVerticalGrid(
                        state = gridState,
                        columns = gridColumns,
                        modifier = Modifier.fillMaxSize().padding(WatermelonSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
                    ) {
                        gridItems(sorted, key = { it.uri }) { item ->
                            val isSelected = if (isMultiSelectMode) {
                                item.uri in selectedItems
                            } else {
                                selection.contains(item.uri)
                            }

                            VideoListItem(
                                item = item,
                                itemSize = currentItemSize,
                                isGrid = true,
                                isScrollingFast = isScrolling,
                                isSelected = isSelected,
                                selectionActive = selection.isActive || isMultiSelectMode,
                                onClick = {
                                    if (isMultiSelectMode) {
                                        selectedItems = if (item.uri in selectedItems) {
                                            selectedItems - item.uri
                                        } else {
                                            selectedItems + item.uri
                                        }
                                    } else if (selection.isActive) {
                                        viewModel.onToggleSelect(item.uri)
                                    } else {
                                        viewModel.markPlayed(item.uri)
                                        PlaybackQueue.set(sorted.map { it.uri })
                                        onVideoClick(item)
                                    }
                                },
                                onLongClick = {
                                    if (!isMultiSelectMode && !selection.isActive) {
                                        isMultiSelectMode = true
                                        selectedItems = setOf(item.uri)
                                    } else {
                                        viewModel.onLongPress(item.uri)
                                    }
                                },
                                onContextMenuClick = {
                                    contextMenuItem = item
                                    showContextMenu = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${selectedItems.size} video(s)?", color = WatermelonColors.DarkOnSurface) },
            text = { Text("This will permanently delete the selected files from your device.", color = WatermelonColors.DarkOnSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    val sender = viewModel.buildDeleteRequest(context.contentResolver)
                    if (sender != null) {
                        deleteLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                        )
                    }
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = WatermelonColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = WatermelonColors.DarkOnSurface)
                }
            }
        )
    }

    if (showPlaylistPicker) {
        var showCreateField by remember { mutableStateOf(false) }
        var newPlaylistName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Add to playlist", color = WatermelonColors.DarkOnSurface) },
            text = {
                Column {
                    if (showCreateField) {
                        TextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("Playlist name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = {
                                showCreateField = false
                                newPlaylistName = ""
                            }) {
                                Text("Cancel", color = WatermelonColors.DarkOnSurfaceVariant)
                            }
                            TextButton(
                                onClick = {
                                    val name = newPlaylistName.trim()
                                    if (name.isNotEmpty()) {
                                        viewModel.createPlaylistAndAddSelected(name)
                                        showPlaylistPicker = false
                                        showCreateField = false
                                        newPlaylistName = ""
                                    }
                                }
                            ) {
                                Text("Create", color = WatermelonColors.DarkOnSurface)
                            }
                        }
                    } else {
                        TextButton(
                            onClick = { showCreateField = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ Create new playlist", color = WatermelonColors.DarkOnSurface)
                        }
                    }

                    val userPlaylists = availablePlaylists.filter {
                        it.type == com.watermelon.common.model.PlaylistType.USER
                    }
                    if (userPlaylists.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    userPlaylists.forEach { playlist ->
                        TextButton(
                            onClick = {
                                viewModel.addSelectedToPlaylist(playlist.id)
                                showPlaylistPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(playlist.name, color = WatermelonColors.DarkOnSurface)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) {
                    Text("Cancel", color = WatermelonColors.DarkOnSurface)
                }
            }
        )
    }
}
