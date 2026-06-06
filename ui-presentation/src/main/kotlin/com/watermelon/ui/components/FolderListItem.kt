package com.watermelon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.watermelon.common.model.FolderNode
import com.watermelon.ui.screens.ItemSize
import com.watermelon.ui.theme.WatermelonTheme

/**
 * Folder item for both list and grid layouts. Shows an initial-letter icon (no thumbnail —
 * thumbnails live on video items), folder name, file count, and total playtime.
 *
 * @param itemSize controls icon and text density.
 * @param isGrid   when true renders as a Column (icon + text stacked) for grid cells;
 *                 when false renders as a Row for list mode.
 */
@Composable
fun FolderListItem(
    folder: FolderNode,
    onClick: (FolderNode) -> Unit,
    modifier: Modifier = Modifier,
    itemSize: ItemSize = ItemSize.MEDIUM,
    isGrid: Boolean = false,
    @Suppress("UNUSED_PARAMETER") isScrollingFast: Boolean = false   // reserved for future use
) {
    val iconDp: Dp = when (itemSize) {
        ItemSize.SMALL  -> if (isGrid) 40.dp else 32.dp
        ItemSize.MEDIUM -> if (isGrid) 52.dp else 40.dp
        ItemSize.LARGE  -> if (isGrid) 68.dp else 56.dp
    }
    val initial = folder.displayName.firstOrNull()?.uppercase() ?: "?"
    val countText = "${folder.itemCount} files"
    val durationText = if (folder.totalDurationMs > 0L) formatDuration(folder.totalDurationMs) else "--:--"
    val metaText = "$countText · $durationText"

    val shape = RoundedCornerShape(12.dp)
    val baseModifier = modifier
        .clip(shape)
        .clickable { onClick(folder) }

    if (isGrid) {
        // Grid cell: icon centred above name.
        Column(
            modifier = baseModifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FolderIcon(initial = initial, size = iconDp)
            Text(
                text      = folder.displayName,
                style     = when (itemSize) {
                    ItemSize.SMALL  -> MaterialTheme.typography.bodySmall
                    ItemSize.MEDIUM -> MaterialTheme.typography.bodyMedium
                    ItemSize.LARGE  -> MaterialTheme.typography.bodyLarge
                },
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
            Text(
                text  = metaText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        // List row: icon → name + meta.
        Row(
            modifier = baseModifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FolderIcon(initial = initial, size = iconDp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = folder.displayName,
                    style = when (itemSize) {
                        ItemSize.SMALL  -> MaterialTheme.typography.bodyMedium
                        ItemSize.MEDIUM -> MaterialTheme.typography.bodyLarge
                        ItemSize.LARGE  -> MaterialTheme.typography.titleSmall
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text  = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FolderIcon(initial: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = initial,
            style      = MaterialTheme.typography.titleMedium,
            color      = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(name = "List MEDIUM LTR")
@Composable
private fun PreviewListMedium() {
    WatermelonTheme(forceRtl = false) {
        FolderListItem(
            folder   = FolderNode("/DCIM/Camera", "Camera", 128, emptyList(), totalDurationMs = 5_400_000L),
            onClick  = {},
            itemSize = ItemSize.MEDIUM,
            isGrid   = false
        )
    }
}

@Preview(name = "Grid LARGE")
@Composable
private fun PreviewGridLarge() {
    WatermelonTheme(forceRtl = false) {
        FolderListItem(
            folder   = FolderNode("/Movies", "Movies", 42, emptyList(), totalDurationMs = 12_600_000L),
            onClick  = {},
            itemSize = ItemSize.LARGE,
            isGrid   = true
        )
    }
}
