package com.watermelon.ui.tv

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.FolderNode
import com.watermelon.ui.components.FolderListItem
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.viewmodel.FolderViewModel

/**
 * D-Pad-optimised folder browser for Android TV (Manifest §8). Each row is [focusable] with a
 * visible focus ring; overscan-aware padding keeps content inside the 10-foot safe area.
 *
 * This screen already had a real focus ring prior to the UI audit pass (the audit's blanket
 * claim of "zero visual treatment across all 3 TV screens" doesn't hold for this file — see
 * Team 3's manifest). What changed here is purely the reskin: the border now uses
 * `WatermelonShapes.card` (matching [FolderListItem]'s own corner radius, so the ring traces
 * the row's actual shape instead of a slightly different one) and `colorScheme.secondary`
 * (Soft Teal) instead of `colorScheme.primary` (Watermelon Red) — Red is reserved for
 * active/selected/accent states elsewhere in the app; Teal is the dedicated "this has D-pad
 * focus" color (see [com.watermelon.ui.theme.PlayerColors.Scheme.iconFocus] for the Canvas-side
 * equivalent used in [TvPlayerControls]), so focus never reads as if the folder were already
 * selected.
 *
 * A focusable Settings row is pinned to the top of the list — until now this screen was the
 * entire TV app's only reachable surface with no path to Settings at all, so VHS/subtitle/
 * tuner-seekbar preferences (all set from [com.watermelon.ui.screens.SettingsScreen]) were
 * unreachable on TV regardless of remote input.
 *
 * This screen is also the TV app's root/home surface (there is no bottom nav bar or side rail
 * on TV — see [com.watermelon.app.MainActivity]'s TV branching of `shouldShowBottomBar`).
 * Rather than introduce a second navigation paradigm, "All Videos" and "Playlists" are pinned
 * rows here alongside Settings, extending the same pattern this screen already established for
 * reaching Settings — so there's one coherent TV home instead of three competing entry points.
 * Folder/playlist rows below them are unchanged.
 */
@Composable
fun TvFolderBrowserScreen(
    viewModel: FolderViewModel,
    onFolderClick: (FolderNode) -> Unit,
    onAllVideosClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val folders by viewModel.folderTree.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            // Overscan-aware padding (~5% of a 1080p frame) for 10-foot readability.
            // Previously an ad hoc 48dp/27dp; now the nearest values on the shared 4dp
            // grid (48dp = xl+md, 28dp = lg+xs) so this screen's margins stay a multiple
            // of the same base unit as every other screen instead of an odd one out.
            .padding(
                horizontal = WatermelonSpacing.xl + WatermelonSpacing.md,
                vertical = WatermelonSpacing.lg + WatermelonSpacing.xs
            ),
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
    ) {
        item(key = "tv_settings_entry") {
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            androidx.compose.material3.Surface(
                onClick = onSettingsClick,
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
                androidx.compose.material3.Text(
                    text = "Settings",
                    modifier = Modifier.padding(WatermelonSpacing.md)
                )
            }
        }
        item(key = "tv_all_videos_entry") {
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            androidx.compose.material3.Surface(
                onClick = onAllVideosClick,
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
                androidx.compose.material3.Text(
                    text = "All Videos",
                    modifier = Modifier.padding(WatermelonSpacing.md)
                )
            }
        }
        item(key = "tv_playlists_entry") {
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            androidx.compose.material3.Surface(
                onClick = onPlaylistsClick,
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
                androidx.compose.material3.Text(
                    text = "Playlists",
                    modifier = Modifier.padding(WatermelonSpacing.md)
                )
            }
        }
        items(folders, key = { it.path }) { folder ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            FolderListItem(
                folder = folder,
                onClick = onFolderClick,
                interactionSource = interaction,
                modifier = Modifier
                    .border(
                        width = if (focused) 3.dp else 0.dp,
                        color = if (focused) MaterialTheme.colorScheme.secondary else Color.Transparent,
                        shape = WatermelonShapes.card
                    )
            )
        }
    }
}
