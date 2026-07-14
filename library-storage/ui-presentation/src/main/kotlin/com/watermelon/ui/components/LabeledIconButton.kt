package com.watermelon.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing

/**
 * An icon button with a visible text label beneath it. Used app-wide in toolbars,
 * action bars, list controls and settings so every icon is identifiable (Issue 11).
 * NOT used in the player control panel (which intentionally has no labels).
 *
 * @param icon drawable resource (Int) or ImageVector from WatermelonIcons
 * @param label visible caption shown under the icon
 * @param onClick tap handler
 * @param active when true, icon + label use the primary/active color
 * @param enabled when false, the button is dimmed and not clickable. Previously this
 *   component had no disabled state at all — every consumer rendered as fully
 *   interactive regardless of whether the action was actually available, and tap
 *   used bare [Modifier.clickable] with no `enabled` gate.
 */
@Composable
fun LabeledIconButton(
    icon: Any,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    val resolvedTint = when {
        !enabled -> tint.copy(alpha = 0.38f) // Material3's standard disabled-content alpha
        active   -> MaterialTheme.colorScheme.primary
        else     -> tint
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(WatermelonShapes.Radius.small))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = WatermelonSpacing.sm, vertical = WatermelonSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs / 2)
    ) {
        when (icon) {
            is ImageVector -> {
                Icon(
                    imageVector       = icon,
                    contentDescription = label,
                    tint               = resolvedTint,
                    modifier           = Modifier.size(24.dp)
                )
            }
            is Int -> {
                Icon(
                    painter            = painterResource(icon),
                    contentDescription = label,
                    tint               = resolvedTint,
                    modifier           = Modifier.size(24.dp)
                )
            }
            else -> {}
        }
        Text(
            text      = label,
            color     = resolvedTint,
            fontSize  = 10.sp,
            textAlign = TextAlign.Center,
            maxLines  = 1
        )
    }
}
