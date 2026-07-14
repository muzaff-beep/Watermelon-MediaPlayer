package com.watermelon.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.watermelon.ui.theme.PlayerColors
import kotlin.math.roundToLong

/**
 * Custom watermelon-themed seek bar. Played-progress + thumb only (no buffer — offline-first).
 * All colors come from [PlayerColors] semantic tokens, so the look retunes centrally.
 *
 * Exposes a slider role via [Modifier.progressBarRangeInfo] / [Modifier.setProgress] so
 * TalkBack and Switch Access can read the current position and adjust it directly,
 * independent of the drag/tap gesture handling used for touch input.
 *
 * @param positionMs current playback position
 * @param durationMs total duration
 * @param onSeek invoked with the target position while scrubbing / on tap
 * @param onScrubChange optional: true when scrubbing starts, false when it ends
 */
@Composable
fun WatermelonSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 4.dp,
    thumbRadius: Dp = 7.dp,
    onScrubChange: (Boolean) -> Unit = {}
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableStateOf(0f) }
    var widthPx by remember { mutableStateOf(1f) }

    // See WatermelonTunerSeekBar's identical DisposableEffect for why this is needed:
    // onDragEnd/onDragCancel never fire if this composable is torn down mid-drag, so
    // without this, scrubbing (and the caller's derived "currently seeking" state) can get
    // stuck true forever.
    DisposableEffect(Unit) {
        onDispose { if (scrubbing) onScrubChange(false) }
    }

    val liveFraction = if (durationMs > 0)
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val fraction = if (scrubbing) scrubFraction else liveFraction
    val colors = PlayerColors.current

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            // Visual track stays thin (drawn below), but the touch/semantics target is at
            // least 48dp tall so the control meets the minimum accessible touch-target size.
            .height(maxOf(thumbRadius * 2 + 8.dp, 48.dp))
            .semantics {
                contentDescription = "Seek bar, ${formatTimeForA11y(positionMs)} of ${formatTimeForA11y(durationMs)}"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = liveFraction,
                    range = 0f..1f
                )
                setProgress { targetFraction ->
                    val clamped = targetFraction.coerceIn(0f, 1f)
                    onSeek((clamped * durationMs).roundToLong())
                    true
                }
            }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    val f = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek((f * durationMs).roundToLong())
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        scrubbing = true; onScrubChange(true)
                        scrubFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        onSeek((scrubFraction * durationMs).roundToLong())
                        scrubbing = false; onScrubChange(false)
                    },
                    onDragCancel = { scrubbing = false; onScrubChange(false) }
                ) { change, _ ->
                    scrubFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
    ) {
        widthPx = size.width
        drawSeekBar(colors, fraction, trackHeight.toPx(), thumbRadius.toPx(), scrubbing)
    }
}

/** "3:45" / "1:02:03" style formatting for the accessibility announcement. */
private fun formatTimeForA11y(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun DrawScope.drawSeekBar(
    colors: PlayerColors.Scheme,
    fraction: Float,
    trackH: Float,
    thumbR: Float,
    scrubbing: Boolean
) {
    val cy = size.height / 2f
    val left = thumbR
    val right = size.width - thumbR
    val usableW = (right - left).coerceAtLeast(0f)
    val fillX = left + usableW * fraction

    // track (dim)
    drawRoundRect(
        color = colors.seekBarTrack,
        topLeft = Offset(left, cy - trackH / 2f),
        size = Size(usableW, trackH),
        cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
    )
    // played fill (watermelon red)
    drawRoundRect(
        color = colors.seekBarFill,
        topLeft = Offset(left, cy - trackH / 2f),
        size = Size((fillX - left).coerceAtLeast(0f), trackH),
        cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
    )
    // thumb: white ring + red core, grows slightly while scrubbing
    val r = if (scrubbing) thumbR * 1.25f else thumbR
    drawCircle(color = colors.seekBarThumbRing, radius = r + 2f, center = Offset(fillX, cy))
    drawCircle(color = colors.seekBarThumb, radius = r, center = Offset(fillX, cy))
}
    
