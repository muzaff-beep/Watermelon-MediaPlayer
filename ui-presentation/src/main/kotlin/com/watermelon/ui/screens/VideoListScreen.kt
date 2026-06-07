package com.watermelon.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.MediaItem
import com.watermelon.ui.components.VelocityGuardImage
import com.watermelon.ui.components.WatermelonLoadingAnimation
import com.watermelon.ui.viewmodel.VideoListViewModel
import kotlinx.coroutines.delay

private enum class VideoSort(val label: String) { NAME("Name"), DURATION("Duration") }

/**
 * Video list for a single folder.
 * - Watermelon Lottie animation shown while videos are loading (list is empty).
 * - ⭐ badge on items never played. Cleared immediately on tap via [VideoListViewModel.markPlayed].
 * - Pull-to-refresh triggers [VideoListViewModel.refresh].
 * - S/M/L size picker with tangible differences; sort with ascending/descending toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    viewModel: VideoListViewModel,
    onVideoClick: (MediaItem) -> Unit,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()

    var currentSort     by remember { mutableStateOf(VideoSort.NAME) }
    var ascending       by remember { mutableStateOf(true) }
    var currentItemSize by remember { mutableStateOf(ItemSize.MEDIUM) }
    var sortMenuOpen    by remember { mutableStateOf(false) }
    var isRefreshing    by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) { onRefresh(); delay(2_000); isRefreshing = false }
    }

    val sorted = remember(videos, currentSort, ascending) {
        val cmp: Comparator<MediaItem> = when (currentSort) {
            VideoSort.NAME     -> compareBy { it.displayName.lowercase() }
            VideoSort.DURATION -> compareByDescending { it.durationMs }
        }
        videos.sortedWith(if (ascending) cmp else Comparator { a, b -> cmp.compare(b, a) })
    }

    val listState   = rememberLazyListState()
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }

    Column(modifier = modifier.fillMaxSize()) {

        // Toolbar.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                TextButton(onClick = { sortMenuOpen = true }) { Text("Sort: ${currentSort.label}") }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    VideoSort.values().forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.label) },
                            onClick = { currentSort = opt; sortMenuOpen = false }
                        )
                    }
                }
            }
            TextButton(onClick = { ascending = !ascending }) { Text(if (ascending) "↑" else "↓") }
            ItemSize.values().forEach { size ->
                TextButton(onClick = { currentItemSize = size }) {
                    Text(
                        text  = size.label,
                        color = if (size == currentItemSize) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Loading state: show Watermelon animation while Phase 2 runs.
        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WatermelonLoadingAnimation(modifier = Modifier.size(160.dp))
                    Text(
                        text  = "Loading videos…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            return@Column
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { isRefreshing = true },
            modifier     = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state   = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(sorted, key = { it.uri }) { item ->
                    VideoRow(
                        item            = item,
                        itemSize        = currentItemSize,
                        isScrollingFast = isScrolling,
                        onClick         = {
                            viewModel.markPlayed(item.uri)
                            onVideoClick(item)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun VideoRow(item: MediaItem, itemSize: ItemSize, isScrollingFast: Boolean, onClick: () -> Unit) {
    val (thumbW, thumbH) = when (itemSize) {
        ItemSize.SMALL  -> 48.dp to 28.dp
        ItemSize.MEDIUM -> 72.dp to 44.dp
        ItemSize.LARGE  -> 100.dp to 62.dp
    }
    val vPad: Dp = when (itemSize) {
        ItemSize.SMALL  -> 6.dp
        ItemSize.MEDIUM -> 10.dp
        ItemSize.LARGE  -> 16.dp
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = vPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VelocityGuardImage(
            uri             = item.uri,
            durationMs      = item.durationMs,
            isScrollingFast = isScrollingFast,
            modifier        = Modifier
                .size(width = thumbW, height = thumbH)
                .clip(RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text     = item.displayName.ifEmpty { item.uri.substringAfterLast('/') },
                    color    = MaterialTheme.colorScheme.onSurface,
                    style    = when (itemSize) {
                        ItemSize.SMALL  -> MaterialTheme.typography.bodySmall
                        ItemSize.MEDIUM -> MaterialTheme.typography.bodyMedium
                        ItemSize.LARGE  -> MaterialTheme.typography.bodyLarge
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.lastPlayedAt == null) {
                    Text("⭐", fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
            val meta = buildString {
                val fmt = formatLabel(item.mimeType); if (fmt.isNotEmpty()) append(fmt)
                val q = qualityLabel(item.height)
                if (q.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(q) }
                if (item.durationMs > 0) { if (isNotEmpty()) append(" · "); append(formatTime(item.durationMs)) }
                if (item.fileSize > 0) { if (isNotEmpty()) append(" · "); append(formatSize(item.fileSize)) }
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = when (itemSize) {
                        ItemSize.SMALL  -> MaterialTheme.typography.labelSmall
                        ItemSize.MEDIUM -> MaterialTheme.typography.bodySmall
                        ItemSize.LARGE  -> MaterialTheme.typography.bodySmall
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun qualityLabel(h: Int) = when {
    h >= 2160 -> "4K"; h >= 1080 -> "1080p"; h >= 720 -> "720p"
    h >= 480  -> "480p"; h > 0 -> "SD"; else -> ""
}

private fun formatLabel(mime: String): String {
    val raw = mime.substringAfterLast('/', "").uppercase()
    return when (raw) { "X-MATROSKA" -> "MKV"; "QUICKTIME" -> "MOV"; "X-MSVIDEO" -> "AVI"; else -> raw.take(8) }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0); val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun formatSize(b: Long) = when {
    b >= 1_073_741_824L -> "%.1f GB".format(b / 1_073_741_824.0)
    b >= 1_048_576L     -> "${b / 1_048_576} MB"
    else                -> "${b / 1_024} KB"
}
