package com.watermelon.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watermelon.ui.R
import com.watermelon.ui.theme.PlayerColors

/**
 * Full-screen lock overlay. Blocks all touch to the player beneath. Two corner lock handles;
 * drag BOTH up past the threshold to unlock. Handles are ALWAYS visible while locked (simpler
 * and more discoverable than hide-until-touch, which was confusing).
 *
 * Each handle owns its own vertical-drag detector and consumes its own events, so dragging one
 * never leaks to the backdrop or the player. The backdrop itself only shows a hint on tap.
 */
@Composable
fun LockOverlay(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    var heightPx by remember { mutableFloatStateOf(1f) }
    var leftProgress by remember { mutableFloatStateOf(0f) }
    var rightProgress by remember { mutableFloatStateOf(0f) }
    var showHint by remember { mutableStateOf(true) }

    val threshold = 0.6f
    var unlocked by remember { mutableStateOf(false) }
    LaunchedEffect(leftProgress, rightProgress) {
        if (leftProgress >= threshold && rightProgress >= threshold) {
            unlocked = true
            onUnlock()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            // Backdrop swallows taps so the player beneath gets nothing.
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, _ -> showHint = true }
            }
    ) {
        if (showHint) {
            Text(
                "Slide both locks up together to unlock",
                color = PlayerColors.current.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
            )
        }
        LockHandle(
            progress = leftProgress,
            onDrag = { delta -> leftProgress = (leftProgress - delta / (heightPx * 0.20f)).coerceIn(0f, 1f) },
            onRelease = { if (!unlocked) leftProgress = 0f },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 40.dp)
        )
        LockHandle(
            progress = rightProgress,
            onDrag = { delta -> rightProgress = (rightProgress - delta / (heightPx * 0.20f)).coerceIn(0f, 1f) },
            onRelease = { if (!unlocked) rightProgress = 0f },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 40.dp)
        )
    }
}

@Composable
private fun LockHandle(
    progress: Float,
    onDrag: (Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animated by animateFloatAsState(progress, label = "lockSlide")
    val open = progress >= 0.6f
    Box(
        modifier = modifier
            .size(56.dp)
            .graphicsLayer { translationY = -animated * 120f }
            .background(PlayerColors.current.sheetBackground.copy(alpha = 0.9f), RoundedCornerShape(28.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { onRelease() },
                    onDragCancel = { onRelease() }
                ) { change, dragAmount ->
                    onDrag(dragAmount)
                    change.consume()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(if (open) R.drawable.ic_lock_open else R.drawable.ic_lock),
            contentDescription = "Slide up to unlock",
            tint = if (open) PlayerColors.current.iconActive else PlayerColors.current.iconDefault,
            modifier = Modifier.size(26.dp)
        )
    }
}
