package com.watermelon.ui.screens

import androidx.compose.foundation.horizontalScroll
import java.util.Collections
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.FolderNode
import com.watermelon.ui.R
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.components.FolderListItem
import com.watermelon.ui.components.LabeledIconButton
import com.watermelon.ui.viewmodel.BrowserRow
import com.watermelon.ui.viewmodel.FolderViewModel

/** Thumbnail density / cell size for folder and video lists. */
enum class ItemSize(val label: String) { SMALL("S"), MEDIUM("M"), LARGE("L") }

enum class FolderLayout { LIST, GRID }
enum class FolderSort   { NAME, DATE, SIZE, RESOLUTION }

private val LayoutSaver = Saver<FolderLayout, String>(
    save    = { it.name },
    restore = { FolderLayout.valueOf(it) }
)
private val SizeSaver = Saver<ItemSize, String>(
    save    = { it.name },
    restore = { ItemSize.valueOf(it) }
)

/**
 * Folder browser, matching wireframe #1:
 *   [labeled toolbar: Grid · Sort · Asc · S/M/L · Settings]
 *   MY VIDEO PLAYLISTS   (eyebrow header)
 *     Recently Added, Favourites, user playlists…
 *   MAIN STORAGE         (eyebrow header)
 *     storage folders…
 *
 * Consumes FolderViewModel.rows (headers + folders) instead of the flat tree, so the
 * section structure and hidden-folder filtering from Phase A are honored.
 */
@Composable
fun FolderBrowserScreen(
    viewModel: FolderViewModel,
    onFolderClick: (FolderNode) -> Unit,
    onSettingsClick: () -> Unit = {},
    layout: FolderLayout = FolderLayout.LIST,
    modifier: Modifier   = Modifier
) {
    val rowsRaw by viewModel.rows.collectAsStateWithLifecycle()

    var currentLayout   by rememberSaveable(stateSaver = LayoutSaver) { mutableStateOf(layout) }
    var currentItemSize by rememberSaveable(stateSaver = SizeSaver)   { mutableStateOf(ItemSize.MEDIUM) }
    var ascending       by rememberSaveable { mutableStateOf(true) }
    var sortMenuOpen    by remember { mutableStateOf(false) }
    var currentSort     by rememberSaveable { mutableStateOf(FolderSort.NAME) }

    // Sort/ascending is applied per section (headers stay in place, folders within each
    // section are reordered) so "MY VIDEO PLAYLISTS" / "MAIN STORAGE" grouping survives.
    // FolderNode currently exposes no date-modified or resolution field, so DATE and
    // RESOLUTION fall back to NAME ordering rather than silently no-opping.
    val rows by remember(rowsRaw, currentSort, ascending) {
        derivedStateOf {
            val baseComparator: Comparator<FolderNode> = when (currentSort) {
                FolderSort.NAME       -> compareBy { it.displayName.lowercase() }
                FolderSort.SIZE       -> compareBy { it.itemCount }
                FolderSort.DATE       -> compareBy { it.displayName.lowercase() } // no backing field yet
                FolderSort.RESOLUTION -> compareBy { it.displayName.lowercase() } // no backing field yet
            }
            val nodeComparator = if (ascending) baseComparator else Collections.reverseOrder(baseComparator)
            val folderComparator = Comparator<BrowserRow.Folder> { a, b -> nodeComparator.compare(a.node, b.node) }

            // Group contiguous Folder rows within each Header section and sort within group,
            // so section grouping ("MY VIDEO PLAYLISTS" / "MAIN STORAGE") is preserved.
            val result = mutableListOf<BrowserRow>()
            var bucket = mutableListOf<BrowserRow.Folder>()
            fun flushBucket() {
                result += bucket.sortedWith(folderComparator)
                bucket = mutableListOf()
            }
            for (row in rowsRaw) {
                when (row) {
                    is BrowserRow.Header -> { flushBucket(); result += row }
                    is BrowserRow.Folder -> bucket += row
                }
            }
            flushBucket()
            result
        }
    }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val isScrolling by remember {
        derivedStateOf { listState.isScrollInProgress || gridState.isScrollInProgress }
    }

    val isGrid = currentLayout == FolderLayout.GRID
    val gridColumns = when (currentItemSize) {
        ItemSize.SMALL  -> GridCells.Fixed(3)
        ItemSize.MEDIUM -> GridCells.Fixed(2)
        ItemSize.LARGE  -> GridCells.Fixed(2)
    }

    Column(modifier = modifier.fillMaxSize()) {

        // ── Toolbar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LabeledIconButton(
                icon    = if (isGrid) WatermelonIcons.ViewList else WatermelonIcons.ViewGrid,
                label   = if (isGrid) "List" else "Grid",
                onClick = { currentLayout = if (isGrid) FolderLayout.LIST else FolderLayout.GRID }
            )
            Box {
                LabeledIconButton(
                    icon    = WatermelonIcons.Sort,
                    label   = "Sort: ${currentSort.label()}${if (currentSort.isUnavailable()) " *" else ""}",
                    onClick = { sortMenuOpen = true }
                )
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    FolderSort.values().forEach { opt ->
                        DropdownMenuItem(
                            text    = { Text(opt.label() + if (opt.isUnavailable()) " (uses Name)" else "") },
                            onClick = { currentSort = opt; sortMenuOpen = false }
                        )
                    }
                }
            }
            LabeledIconButton(
                icon    = if (ascending) R.drawable.ic_sort_ascending else R.drawable.ic_sort_descending,
                label   = if (ascending) "Ascending" else "Descending",
                onClick = { ascending = !ascending }
            )
            ItemSize.values().forEach { size ->
                LabeledIconButton(
                    icon    = when (size) {
                        ItemSize.SMALL  -> R.drawable.ic_size_small
                        ItemSize.MEDIUM -> R.drawable.ic_size_medium
                        ItemSize.LARGE  -> R.drawable.ic_size_large
                    },
                    label   = size.label,
                    active  = size == currentItemSize,
                    onClick = { currentItemSize = size }
                )
            }
            LabeledIconButton(
                icon    = WatermelonIcons.Settings,
                label   = "Settings",
                onClick = onSettingsClick
            )
        }

        // ── Content ─────────────────────────────────────────────────────────
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No media folders found", style = MaterialTheme.typography.bodyLarge)
            }
            return@Column
        }

        when (currentLayout) {
            FolderLayout.LIST -> LazyColumn(
                state   = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                rows.forEach { row ->
                    when (row) {
                        is BrowserRow.Header -> item(key = "hdr-${row.title}") {
                            SectionHeader(row.title)
                        }
                        is BrowserRow.Folder -> item(key = row.node.path) {
                            FolderListItem(
                                folder          = row.node,
                                onClick         = onFolderClick,
                                itemSize        = currentItemSize,
                                isGrid          = false,
                                isScrollingFast = isScrolling
                            )
                        }
                    }
                }
            }

            FolderLayout.GRID -> LazyVerticalGrid(
                state   = gridState,
                columns = gridColumns,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEach { row ->
                    when (row) {
                        is BrowserRow.Header -> item(
                            key  = "hdr-${row.title}",
                            span = { GridItemSpan(maxLineSpan) }
                        ) { SectionHeader(row.title) }
                        is BrowserRow.Folder -> gridItems(
                            listOf(row.node), key = { it.path }
                        ) { folder ->
                            FolderListItem(
                                folder          = folder,
                                onClick         = onFolderClick,
                                itemSize        = currentItemSize,
                                isGrid          = true,
                                isScrollingFast = isScrolling
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Eyebrow section header: "MY VIDEO PLAYLISTS" / "MAIN STORAGE". */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text       = title.uppercase(),
        color      = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize   = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier   = Modifier.fillMaxWidth().padding(start = 12.dp, top = 14.dp, bottom = 4.dp)
    )
}

private fun FolderSort.label() = when (this) {
    FolderSort.NAME -> "Name"; FolderSort.DATE -> "Date"
    FolderSort.SIZE -> "Count"; FolderSort.RESOLUTION -> "Resolution"
}

/** True for sort modes with no backing field on [FolderNode] yet (falls back to Name). */
private fun FolderSort.isUnavailable() = this == FolderSort.DATE || this == FolderSort.RESOLUTION
