package com.watermelon.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.watermelon.common.model.FolderNode
import com.watermelon.ui.theme.WatermelonTheme

/**
 * A folder row: velocity-guarded thumbnail, display name, and item count.
 * RTL-aware — the Row mirrors automatically under RTL LayoutDirection.
 *
 * @param isScrollingFast passed from the parent list state; switches the thumbnail between
 *   a cheap MediaStore thumb (fast fling) and Coil quality (settled).
 */
@Composable
fun FolderListItem(
    folder: FolderNode,
    onClick: (FolderNode) -> Unit,
    modifier: Modifier = Modifier,
    isScrollingFast: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick(folder) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        VelocityGuardImage(
            uri = folder.thumbnailUri,
            isScrollingFast = isScrollingFast,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = folder.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = folder.itemCount.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(name = "FolderListItem LTR")
@Composable
private fun FolderListItemPreviewLtr() {
    WatermelonTheme(forceRtl = false) {
        FolderListItem(FolderNode("/Movies", "Movies", 42, emptyList()), onClick = {})
    }
}

@Preview(name = "FolderListItem RTL")
@Composable
private fun FolderListItemPreviewRtl() {
    WatermelonTheme(forceRtl = true) {
        FolderListItem(FolderNode("/فیلم‌ها", "فیلم‌ها", 42, emptyList()), onClick = {})
    }
}
