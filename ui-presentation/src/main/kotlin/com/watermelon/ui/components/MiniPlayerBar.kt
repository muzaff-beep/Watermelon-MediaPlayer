package com.watermelon.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing

private val MiniPlayerHeight = 64.dp

/**
 * Docked persistent mini-player overlay. Appears whenever a video is playing and the user has
 * navigated away from the full player screen (Settings, Library, Folders, etc.) — see
 * [com.watermelon.app.MainActivity]'s `showMiniPlayer` gating, which is the single source of
 * truth for when this composable is present in the tree at all. Nothing here decides
 * visibility itself; it is purely presentational plus its own action callbacks.
 *
 * Layout, left to right: live video surface (not a static frame — [videoSurface] is expected
 * to be the same [androidx.media3.ui.PlayerView]-backed composable the full player uses, just
 * constrained to this bar's height) · title · Play/Pause · Next · Previous · Mute · Close.
 * A thin [LinearProgressIndicator] spans the full bar width along the bottom edge.
 *
 * The progress bar is READ-ONLY (display only, not scrubbable) — deliberate per spec.
 *
 * Tapping anywhere on the bar outside the control buttons invokes [onRestore]. The control
 * buttons each stop click propagation to the row via their own `IconButton` click handling, so
 * a tap on Play/Pause/Next/Previous/Mute/Close never also triggers restore.
 */
@Composable
fun MiniPlayerBar(
    visible: Boolean,
    title: String,
    isPlaying: Boolean,
    isMuted: Boolean,
    progressFraction: Float,
    hasNext: Boolean,
    hasPrevious: Boolean,
    videoSurface: @Composable (Modifier) -> Unit,
    onRestore: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onMuteToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(220)) { -it } + fadeIn(tween(220)),
        exit = slideOutVertically(animationSpec = tween(180)) { -it } + fadeOut(tween(180)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onRestore)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MiniPlayerHeight)
                    .padding(horizontal = WatermelonSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = WatermelonSpacing.xs)
                        .aspectRatio(16f / 9f)
                        .clip(WatermelonShapes.card)
                ) {
                    videoSurface(Modifier.fillMaxSize())
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = WatermelonSpacing.sm)
                )

                IconButton(onClick = onPrevious, enabled = hasPrevious) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        tint = if (hasPrevious) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
                IconButton(onClick = onNext, enabled = hasNext) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = if (hasNext) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
                IconButton(onClick = onMuteToggle) {
                    Icon(
                        if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute"
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            LinearProgressIndicator(
                progress = { progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            )
        }
    }
}
