package com.watermelon.ui.tv

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.MediaItem
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.viewmodel.VideoListViewModel
import kotlinx.coroutines.launch

/**
 * D-Pad-optimised video list for Android TV. Serves both the All Videos destination and any
 * folder/playlist's contents — same role as the phone [com.watermelon.ui.screens.VideoListScreen]
 * for those two call sites, but per this module's convention (phone/TV as separate compositions
 * sharing a data layer, not one screen with conditionals) this is a new composition rather than
 * a retrofit of that screen.
 *
 * Deliberately excluded, matching TV scope: the phone screen's horizontalScroll sort/layout
 * toolbar (drag/scroll chrome with no D-pad affordance) and long-press multi-select (no
 * long-press concept on a D-pad — OK is a single discrete action). A TV viewer sorts by walking
 * a plain list; batch delete/share/playlist-add from a video list is out of scope for this pass
 * the same way multi-select already was for the other new TV screens.
 *
 * Row order matches [VideoListViewModel.videos] as emitted (no client-side re-sort), consistent
 * with the "no sort chrome" scope decision above.
 */
@Composable
fun TvVideoListScreen(
    viewModel: VideoListViewModel,
    title: String,
    onVideoClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = WatermelonSpacing.xl + WatermelonSpacing.md,
                    vertical = WatermelonSpacing.sm
                )
        )

        if (videos.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = WatermelonSpacing.xl + WatermelonSpacing.md),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No videos here yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = WatermelonSpacing.xl + WatermelonSpacing.md,
                    vertical = WatermelonSpacing.xs
                ),
            verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
        ) {
            items(videos, key = { it.uri }) { item ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                Surface(
                    onClick = {
                        // Mirrors VideoListScreen's phone-side click handler: mark played,
                        // then resolve+seed PlaybackQueue BEFORE navigating — without this,
                        // TvPlayerScreen's next/prev and auto-advance-on-end have nothing to
                        // read (empty queue), and the Continue-Watching-never-skip-itself
                        // scoping (resolvePlaybackQueueUris routes to the video's real
                        // parent folder when opened from Continue Watching) never gets a
                        // chance to apply, since it's only invoked here.
                        viewModel.markPlayed(item.uri)
                        coroutineScope.launch {
                            val queueUris = viewModel.resolvePlaybackQueueUris(item.uri, videos)
                            com.watermelon.ui.screens.PlaybackQueue.set(queueUris)
                            onVideoClick(item)
                        }
                    },
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
                                text = item.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(WatermelonSpacing.md))
                        Text(
                            text = formatTvDuration(item.durationMs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatTvDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
