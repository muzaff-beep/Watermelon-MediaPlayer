package com.watermelon.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.watermelon.common.model.UserIntent
import com.watermelon.ui.theme.PlayerColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing

/**
 * D-Pad player controls (Manifest §8). Left/Right hold seeks (10s per repeat, matching the
 * phone screen's swipe-to-seek granularity); Up/Down nudges subtitle sync in +/-100ms steps.
 * Transport buttons (Previous / Play-Pause / Next) are focusable for remote navigation and are
 * the guaranteed single-button-reachable controls — OK on Play/Pause toggles playback, which
 * satisfies the "works with a partially-broken remote" requirement even if D-pad directions
 * are the only other working input.
 *
 * Previous/Next are omitted entirely (not just disabled) when there's no adjacent track, same
 * convention as [com.watermelon.ui.screens.PhonePlayerScreen], so the row never shows a dead
 * button a D-pad user could focus onto and press for no effect.
 */
@Composable
fun TvPlayerControls(
    isPlaying: Boolean,
    hasPreviousTrack: Boolean,
    hasNextTrack: Boolean,
    onIntent: (UserIntent) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSubtitleNudge: (Long) -> Unit,
    onSeekHold: (direction: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(WatermelonSpacing.lg)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeekHold(-1); true }
                    Key.DirectionRight -> { onSeekHold(+1); true }
                    Key.DirectionUp -> { onSubtitleNudge(+100L); true }
                    Key.DirectionDown -> { onSubtitleNudge(-100L); true }
                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(WatermelonSpacing.md)
    ) {
        if (hasPreviousTrack) {
            TvFocusableButton(label = "Previous", onClick = onSkipPrevious)
        }
        TvFocusableButton(
            label = if (isPlaying) "Pause" else "Play",
            onClick = { onIntent(if (isPlaying) UserIntent.Pause else UserIntent.Resume) }
        )
        if (hasNextTrack) {
            TvFocusableButton(label = "Next", onClick = onSkipNext)
        }
    }
}

/**
 * Shared focus treatment for D-pad transport buttons: a visible border that appears only
 * while focused, plus a small scale animation so the change reads clearly from a 10-foot
 * viewing distance without relying on color alone.
 */
@Composable
private fun TvFocusableButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "tvButtonFocusScale")

    Button(
        onClick = onClick,
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = PlayerColors.current.sheetBackground,
            contentColor = PlayerColors.current.textPrimary
        ),
        modifier = modifier
            .scale(scale)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) PlayerColors.current.iconFocus else Color.Transparent,
                shape = RoundedCornerShape(WatermelonShapes.Radius.control)
            )
    ) {
        Text(label)
    }
}
