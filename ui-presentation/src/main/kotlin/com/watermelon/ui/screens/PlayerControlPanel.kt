package com.watermelon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watermelon.common.model.RepeatMode
import com.watermelon.ui.R
import com.watermelon.ui.theme.PlayerColors

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
fun ControlPanel(
    modifier: Modifier,
    currentSpeed: Float,
    isMuted: Boolean,
    currentRatio: VideoRatio,
    repeatMode: RepeatMode,
    isShuffled: Boolean,
    isPiP: Boolean,
    isBackground: Boolean,
    onSpeedChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onRatioChange: (VideoRatio) -> Unit,
    currentOrientation: ScreenOrientation,
    onOrientationChange: (ScreenOrientation) -> Unit,
    onRepeat: () -> Unit, onShuffle: () -> Unit, onScreenshot: () -> Unit,
    onSleepTimer: () -> Unit, onPip: () -> Unit, onBackground: () -> Unit, onPlaylist: () -> Unit,
    onShare: () -> Unit, onFavourite: () -> Unit, onAddToPlaylist: () -> Unit, onDelete: () -> Unit
) {
    Column(
        modifier = modifier
            .background(PlayerColors.sheetBackground.copy(alpha = 0.95f), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PanelLabel("Speed")
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            SPEEDS.forEach { speed ->
                val active = speed == currentSpeed
                TextButton(onClick = { onSpeedChange(speed) }, modifier = Modifier.height(32.dp)) {
                    Text(formatSpeed(speed), color = if (active) PlayerColors.iconActive else PlayerColors.iconDefault,
                        fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        PanelLabel("Ratio")
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            VideoRatio.values().forEach { ratio ->
                val active = ratio == currentRatio
                TextButton(onClick = { onRatioChange(ratio) }, modifier = Modifier.height(32.dp)) {
                    Text(ratio.label, color = if (active) PlayerColors.iconActive else PlayerColors.iconDefault,
                        fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        PanelLabel("Rotate")
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ScreenOrientation.values().forEach { orientation ->
                val active = orientation == currentOrientation
                IconButton(onClick = { onOrientationChange(orientation) }) {
                    Icon(painterResource(orientation.iconRes), orientation.name,
                        tint = if (active) PlayerColors.iconActive else PlayerColors.iconDefault,
                        modifier = Modifier.width(20.dp).height(20.dp))
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconStub(R.drawable.ic_volume_mute, "Mute", isMuted, onMuteToggle)
            val repeatIcon = when (repeatMode) {
                RepeatMode.NONE -> R.drawable.ic_repeat_off
                RepeatMode.ONE -> R.drawable.ic_repeat_one
                RepeatMode.ALL -> R.drawable.ic_repeat_all
            }
            IconStub(repeatIcon, "Repeat", repeatMode != RepeatMode.NONE, onRepeat)
            IconStub(if (isShuffled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off, "Shuffle", isShuffled, onShuffle)
            IconStub(R.drawable.ic_screenshot_single, "Screenshot", false, onScreenshot)
            IconStub(R.drawable.ic_sleep_timer, "Sleep timer", false, onSleepTimer)
            IconStub(R.drawable.ic_pip, "PiP", isPiP, onPip)
            IconStub(R.drawable.ic_background_play, "Background play", isBackground, onBackground)
            IconStub(R.drawable.ic_playlist_add, "Playlist", false, onPlaylist)
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        PanelLabel("File")
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconStub(R.drawable.ic_share, "Share", false, onShare)
            IconStub(R.drawable.ic_favorite, "Add to favourites", false, onFavourite)
            IconStub(R.drawable.ic_playlist_add, "Add to playlist", false, onAddToPlaylist)
            IconStub(R.drawable.ic_delete, "Delete", false, onDelete)
        }
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(text, color = PlayerColors.textSecondary, fontSize = 11.sp)
}

@Composable
private fun IconStub(iconRes: Int, description: String, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(iconRes), description,
            tint = if (active) PlayerColors.iconActive else PlayerColors.iconDefault,
            modifier = Modifier.width(22.dp).height(22.dp))
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toLong().toFloat()) "${speed.toLong()}×" else "${speed}×"
