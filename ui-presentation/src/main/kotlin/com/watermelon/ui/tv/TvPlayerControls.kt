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
 * D-Pad player controls (Manifest §8). Left/Right hold accelerates seek (2×/4×/8×) and
 * triggers the VHS animation; Up/Down nudges subtitle sync. Transport buttons are focusable
 * for remote navigation.
 *
 * **Priority fix (UI audit):** every button previously used bare `Modifier.focusable()` with
 * no visual treatment at all — a D-pad user had no way to see which button was focused before
 * pressing OK. Each button now gets a visible border + a subtle scale-up on focus, using
 * [PlayerColors.current.iconFocus] (Soft Teal) — a token Team 0 defined for exactly this
 * purpose but that had no consumer anywhere in the codebase until now. Teal (focus) is kept
 * deliberately distinct from Red ([PlayerColors.current.accent], used for pressed/active
 * state elsewhere), so "this is focused" and "this is active" never look the same on a TV
 * screen where hover doesn't exist.
 */
@Composable
fun TvPlayerControls(
    onIntent: (UserIntent) -> Unit,
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
        TvFocusableButton(label = "Play", onClick = { onIntent(UserIntent.Resume) })
        TvFocusableButton(label = "Pause", onClick = { onIntent(UserIntent.Pause) })
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