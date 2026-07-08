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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.watermelon.common.model.MediaItem
import com.watermelon.ui.R
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

/**
 * Video list item component with context menu support.
 * Implements the design system requirements for playlist cards.
 */
enum class ItemSize(val label: String) { SMALL("S"), MEDIUM("M"), LARGE("L") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
    item: MediaItem,
    itemSize: ItemSize,
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
        ItemSize.SMALL -> if (isGrid) 72.dp else 40.dp
        ItemSize.MEDIUM -> if (isGrid) 120.dp else 64.dp
        ItemSize.LARGE -> if (isGrid) 180.dp else 96.dp
    }
    val textStyle = when (itemSize) {
        ItemSize.SMALL -> MaterialTheme.typography.bodySmall
        ItemSize.MEDIUM -> MaterialTheme.typography.bodyMedium
        ItemSize.LARGE -> MaterialTheme.typography.bodyLarge
    }

    val selectedBorder = if (isSelected) {
        Modifier.border(2.dp, WatermelonColors.Accent, WatermelonShapes.control)
    } else {
        Modifier
    }

    val clickModifier = Modifier
        .clip(WatermelonShapes.control)
        .then(selectedBorder)
        .combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )

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

                // Context menu button (top-right)
                if (!selectionActive) {
                    IconButton(
                        onClick = onContextMenuClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(WatermelonSpacing.xs)
                            .size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = WatermelonColors.DarkOnSurface,
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

                // Context menu button (on thumbnail)
                if (!selectionActive) {
                    IconButton(
                        onClick = onContextMenuClick,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play_arrow),
                            contentDescription = "Play",
                            tint = WatermelonColors.PaperWhite.copy(alpha = 0.8f)
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = WatermelonColors.DarkOnSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.lastPlayedAt == null) {
                        StatusBadge.New(modifier = Modifier.padding(start = WatermelonSpacing.xs))
                    }
                }
                Text(
                    text = formatDuration(item.durationMs),
                    style = WatermelonTypography.timecode,
                    color = WatermelonColors.DarkOnSurfaceVariant
                )
            }

            // Context menu button (end of row)
            if (!selectionActive) {
                IconButton(
                    onClick = onContextMenuClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
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

@Composable
private fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(WatermelonShapes.sharp)
            .combinedClickable(onClick = onClick)
    ) {
        // Content will be provided by caller
    }
}