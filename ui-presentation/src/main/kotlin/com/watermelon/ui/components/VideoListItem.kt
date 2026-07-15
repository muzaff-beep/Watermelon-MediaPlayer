package com.watermelon.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.watermelon.common.model.MediaItem
import com.watermelon.ui.R
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

/** Video list's own size axis — SMALL (compact row with extra metadata: resolution, file
 *  size, date added) and LARGE (big, simple row: name + duration only). Deliberately only
 *  2 values, unlike folders/playlists' [ItemSize] below, which keeps 3 (SMALL/MEDIUM/LARGE)
 *  — the two screens' sizing scales are independent by design. */
enum class VideoItemSize(val label: String) { SMALL("S"), LARGE("L") }

/** Folders/playlists' own size axis (used by FolderListItem/FolderBrowserScreen) — kept at
 *  3 values, unlike the video list's [VideoItemSize] above. Small rows there also show
 *  extra metadata (item count, duration, last modified — see FolderListItem's
 *  smallMetaText). */
enum class ItemSize(val label: String) { SMALL("S"), MEDIUM("M"), LARGE("L") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
    item: MediaItem,
    itemSize: VideoItemSize,
    isGrid: Boolean,
    isScrollingFast: Boolean,
    isSelected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onContextMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val thumbH: Dp = when (itemSize) {
        VideoItemSize.SMALL -> if (isGrid) 72.dp else 48.dp
        VideoItemSize.LARGE -> if (isGrid) 180.dp else 96.dp
    }
    val textStyle = when (itemSize) {
        VideoItemSize.SMALL -> WatermelonTypography.typography.bodyMedium
        VideoItemSize.LARGE -> WatermelonTypography.typography.bodyLarge
    }

    val selectedBorder = if (isSelected) {
        Modifier.border(2.dp, WatermelonColors.Accent, WatermelonShapes.control)
    } else {
        Modifier
    }

    val clickModifier = Modifier
        .clip(WatermelonShapes.control)
        .then(selectedBorder)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)

    if (isGrid) {
        Column(
            modifier = modifier.then(clickModifier).padding(WatermelonSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs)
        ) {
            Box {
                VelocityGuardImage(
                    uri = item.uri,
                    durationMs = item.durationMs,
                    isScrollingFast = isScrollingFast,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(thumbH)
                        .clip(WatermelonShapes.small)
                )

                if (!selectionActive) {
                    androidx.compose.material3.IconButton(
                        onClick = onContextMenuClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(WatermelonSpacing.xs)
                            .size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vertical),
                            contentDescription = "More options",
                            tint = WatermelonColors.DarkSurface
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.displayName,
                    style = textStyle,
                    color = WatermelonColors.DarkOnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.lastPlayedAt == null) {
                    StatusBadge.New(compact = true)
                }
            }

            Text(
                text = formatDuration(item.durationMs),
                style = WatermelonTypography.timecode,
                color = WatermelonColors.DarkOnSurfaceVariant
            )
        }
    } else {
        Row(
            modifier = modifier.then(clickModifier)
                .fillMaxWidth()
                .padding(horizontal = WatermelonSpacing.sm, vertical = WatermelonSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                VelocityGuardImage(
                    uri = item.uri,
                    durationMs = item.durationMs,
                    isScrollingFast = isScrollingFast,
                    modifier = Modifier
                        .width(thumbH * 16f / 9f)
                        .height(thumbH)
                        .clip(WatermelonShapes.small)
                )

                if (!selectionActive) {
                    androidx.compose.material3.IconButton(
                        onClick = onContextMenuClick,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play_arrow),
                            contentDescription = "Play",
                            tint = WatermelonColors.Palette.PaperWhite.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(Modifier.width(WatermelonSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.displayName,
                        style = textStyle,
                        color = WatermelonColors.DarkOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.lastPlayedAt == null) {
                        StatusBadge.New(modifier = Modifier.padding(start = WatermelonSpacing.xs))
                    }
                }
                // Parent folder name — list view only (grid rows are too compact for it,
                // and the spec explicitly calls out grid as unaffected). Shown regardless
                // of Small/Large: Large appends it after duration since that's its only
                // metadata line; Small appends it to its existing resolution/size/date line.
                val folderSuffix = if (!isGrid) " · ${item.parentFolder}" else ""
                Text(
                    text = formatDuration(item.durationMs) + if (itemSize == VideoItemSize.LARGE) folderSuffix else "",
                    style = WatermelonTypography.timecode,
                    color = WatermelonColors.DarkOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (itemSize == VideoItemSize.SMALL) {
                    Text(
                        text = formatDetailLine(item) + folderSuffix,
                        style = WatermelonTypography.typography.bodySmall,
                        color = WatermelonColors.DarkOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!selectionActive) {
                androidx.compose.material3.IconButton(
                    onClick = onContextMenuClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vertical),
                        contentDescription = "More options",
                        tint = WatermelonColors.DarkOnSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** "1920x1080 · 245 MB · Jan 3, 2026" — the extra detail line shown on Small rows. Any
 *  piece with no known value (0) is dropped rather than shown as "0x0" or "Jan 1, 1970",
 *  since those are more confusing than just omitting the field for a file the indexer
 *  hasn't fully resolved yet. */
private fun formatDetailLine(item: com.watermelon.common.model.MediaItem): String {
    val parts = mutableListOf<String>()
    if (item.width > 0 && item.height > 0) parts += "${item.width}x${item.height}"
    if (item.fileSize > 0) parts += formatFileSize(item.fileSize)
    if (item.dateAdded > 0) parts += formatDateAdded(item.dateAdded)
    return parts.joinToString(" · ")
}

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.1f GB".format(gb)
        mb >= 1.0 -> "%.0f MB".format(mb)
        else -> "%.0f KB".format(kb)
    }
}

private fun formatDateAdded(epochMs: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochMs))
}