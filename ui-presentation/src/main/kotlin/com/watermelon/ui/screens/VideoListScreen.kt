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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.Playlist
import com.watermelon.ui.R
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.components.LabeledIconButton
import com.watermelon.ui.components.MultiSelectionDock
import com.watermelon.ui.components.StatusBadge
import com.watermelon.ui.components.VideoContextMenu
import com.watermelon.ui.components.VideoListItem
import com.watermelon.ui.components.VelocityGuardImage
import com.watermelon.ui.components.WatermelonHeader
import com.watermelon.ui.components.WatermelonLoadingAnimation
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography
import com.watermelon.ui.viewmodel.VideoListViewModel
import kotlinx.coroutines.delay

private enum class VideoSort(val label: String) {
    NAME("Name"), DATE("Date"), DURATION("Duration"),
    FILE_TYPE("File Type"), SIZE("Size"), QUALITY("Quality"), CUSTOM("Custom")
}

private enum class VideoLayout { LIST, GRID }

private val LayoutSaver = androidx.compose.runtime.saveable.Saver<VideoLayout, String>(
    save = { it.name },
    restore = { VideoLayout.valueOf(it) }
)

private val SortSaver2 = androidx.compose.runtime.saveable.Saver<VideoSort, String>(
    save = { it.name },
    restore = { VideoSort.valueOf(it) }
)

@OptIn(ExperimentalFoundationApi::class)
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

    var currentSort by rememberSaveable(stateSaver = SortSaver2) { mutableStateOf(VideoSort.NAME) }
    var ascending by rememberSaveable { mutableStateOf(true) }
    var currentItemSize by rememberSaveable(stateSaver = com.watermelon.ui.components.ItemSizeSaver) {
        mutableStateOf(com.watermelon.ui.components.ItemSize.MEDIUM)
    }
    var currentLayout by rememberSaveable(stateSaver = LayoutSaver) { mutableStateOf(VideoLayout.LIST) }
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
        if (isRefreshing) { onRefresh(); delay(2_000); isRefreshing = false }
    }

    val sorted = remember(videos, currentSort, ascending) {
        if (currentSort == VideoSort.CUSTOM) {
            videos
        } else {
            val cmp: Comparator<MediaItem> = when (currentSort) {
                VideoSort.NAME -> compareBy { it.displayName.lowercase() }
                VideoSort.DATE -> compareByDescending { if (it.dateAdded > 0L) it.dateAdded else it.firstSeenAt }