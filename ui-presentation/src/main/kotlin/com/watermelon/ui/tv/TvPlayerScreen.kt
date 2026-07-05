package com.watermelon.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.watermelon.ui.theme.PlayerColors

/**
 * TV player composition — PLACEHOLDER (parked).
 *
 * TODO: TV UX — design later, with care. Requirements already agreed:
 *   - D-pad-first: every action reachable by up/down/left/right + OK only.
 *   - Must work with a partially-broken remote (no reliance on Back/Menu/color buttons).
 *   - OK = play/pause as the guaranteed single-button control.
 *   - Minimal action set: drop brightness, pinch-zoom, PiP, rotation.
 *   - Large, always-reachable focus targets; simple linear focus order.
 *   - Large horizontal seekbar navigable by left/right hold.
 *
 * For now this stub simply renders the shared video surface so TV devices can still play.
 */
@Composable
fun TvPlayerScreen(
    surface: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(PlayerColors.current.background),
        contentAlignment = Alignment.Center) {
        surface(Modifier.fillMaxSize())
        // Intentionally minimal — full TV control UX is a separate, deliberate chapter.
        Text("", color = PlayerColors.current.textPrimary)
    }
}
