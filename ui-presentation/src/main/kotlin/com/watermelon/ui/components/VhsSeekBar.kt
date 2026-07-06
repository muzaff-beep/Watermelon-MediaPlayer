package com.watermelon.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.watermelon.ui.theme.PlayerColors
import kotlin.math.abs

/**
 * Single custom-drawn seek bar — one track, one thumb, one gesture system.
 *
 * Replaces the previous Material3 Slider implementation, which had two problems:
 *  1. The Slider's own drag handling (onValueChange) fought with a second
 *     detectHorizontalDragGestures attached via pointerInput — two gesture systems
 *     competing for the same touch caused the stuttering / unsmooth controls.
 *  2. The Slider always draws its own track, which read as a second bar stacked
 *     against the controls-overlay layout edge (the "double seekbar").
 *
 * This version draws the track and thumb itself with Canvas, so there is exactly one
 * visible bar and exactly one place that consumes drag. VHS velocity tracking
 * (2×/4×/8×) is derived from the same single drag stream.
 *
 * @param onSeek            committed seek target in ms.
 * @param onSeekingFastChange true while the user is actively dragging.
 * @param onSeekSpeedChange 2 / 4 / 8 during drag, 0 when released.
 */
@Composable
fun VhsSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onSeekingFastChange: (Boolean) -> Unit,
    onSeekSpeedChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // While dragging we show the finger's fraction; otherwise we follow playback position.
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val playbackFraction =
        if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownFraction = if (dragging) dragFraction else playbackFraction

    val colors        = PlayerColors.current
    val activeColor   = colors.seekBarFill
    val inactiveColor = colors.seekBarTrack
    val thumbColor    = colors.seekBarThumb

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            // ONE gesture system. Tap to scrub-to-point; drag to seek + report velocity.
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek((frac * durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekingFastChange(true)
                    },
                    onDragEnd = {
                        dragging = false
                        onSeek((dragFraction * durationMs).toLong())
                        onSeekingFastChange(false)
                        onSeekSpeedChange(0)
                    },
                    onDragCancel = {
                        dragging = false
                        onSeekingFastChange(false)
                        onSeekSpeedChange(0)
                    }
                ) { change, dragAmount ->
                    // Advance the thumb by the drag delta, as a fraction of bar width.
                    dragFraction = (dragFraction + dragAmount / size.width).coerceIn(0f, 1f)
                    onSeek((dragFraction * durationMs).toLong())
                    // Map drag speed to the analog tape-tracking multiplier.
                    onSeekSpeedChange(
                        when {
                            abs(dragAmount) > 40f -> 8
                            abs(dragAmount) > 20f -> 4
                            else -> 2
                        }
                    )
                    change.consume()
                }
            }
    ) {
        val barY      = size.height / 2f
        val trackH    = 4.dp.toPx()
        val thumbR    = 8.dp.toPx()
        val activeEnd = size.width * shownFraction

        // Inactive track (full width).
        drawLine(
            color = inactiveColor,
            start = Offset(0f, barY),
            end   = Offset(size.width, barY),
            strokeWidth = trackH
        )
        // Active track (up to the thumb).
        drawLine(
            color = activeColor,
            start = Offset(0f, barY),
            end   = Offset(activeEnd, barY),
            strokeWidth = trackH
        )
        // Thumb.
        drawCircle(
            color  = thumbColor,
            radius = thumbR,
            center = Offset(activeEnd.coerceIn(thumbR, size.width - thumbR), barY)
        )
    }
}
