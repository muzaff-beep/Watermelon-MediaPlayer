package com.watermelon.ui.screens

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.FolderNode
import com.watermelon.ui.components.FolderListItem
import com.watermelon.ui.viewmodel.FolderViewModel

/** Thumbnail density / cell size for folder and video lists. */
enum class ItemSize(val label: String) { SMALL("S"), MEDIUM("M"), LARGE("L") }

enum class FolderLayout { LIST, GRID }
enum class FolderSort   { NAME, DATE, SIZE, RESOLUTION }

@Composable
fun FolderBrowserScreen(
    viewModel: FolderViewModel,
    onFolderClick: (FolderNode) -> Unit,
    layout: FolderLayout = FolderLayout.LIST,
    sort: FolderSort     = FolderSort.NAME,
    modifier: Modifier   = Modifier
) {
    val folders by viewModel.folderTree.collectAsStateWithLifecycle()

    var currentLayout   by remember { mutableStateOf(layout) }
    var currentSort     by remember { mutableStateOf(sort) }
    var currentItemSize by remember { mutableStateOf(ItemSize.MEDIUM) }
    var ascending       by remember { mutableStateOf(true) }
    var sortMenuOpen    by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val isScrolling by remember {
        derivedStateOf { listState.isScrollInProgress || gridState.isScrollInProgress }
    }

    val byVolume = remember(folders, currentSort, ascending) {
        val cmp = sortComparator(currentSort)
            .let { if (ascending) it else Comparator { a, b -> it.compare(b, a) } }
        folders.groupBy { it.volume }
            .toSortedMap()
            .mapValues { (_, v) -> v.sortedWith(cmp) }
    }

    val isGrid = currentLayout == FolderLayout.GRID

    // Grid columns: responsive to size selection so large items always fill the row.
    val gridColumns = when (currentItemSize) {
        ItemSize.SMALL  -> GridCells.Fixed(3)
        ItemSize.MEDIUM -> GridCells.Fixed(2)
        ItemSize.LARGE  -> GridCells.Fixed(2)
    }

    Column(modifier = modifier.fillMaxSize()) {

        // ── Toolbar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                currentLayout = if (isGrid) FolderLayout.LIST else FolderLayout.GRID
            }) { Text(if (isGrid) "List" else "Grid") }

            Box {
                TextButton(onClick = { sortMenuOpen = true }) {
                    Text("Sort: ${currentSort.label()}")
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    FolderSort.values().forEach { opt ->
                        DropdownMenuItem(
                            text    = { Text(opt.label()) },
                            onClick = { currentSort = opt; sortMenuOpen = false }
                        )
                    }
                }
            }

            TextButton(onClick = { ascending = !ascending }) {
                Text(if (ascending) "↑" else "↓")
            }

            ItemSize.values().forEach { size ->
                TextButton(onClick = { currentItemSize = size }) {
                    Text(
                        text  = size.label,
                        color = if (size == currentItemSize) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // ── Content ───────────────────────────────────────────────────────────
        if (folders.isEmpty()) {
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
                byVolume.forEach { (volume, vfolders) ->
                    item(key = "hdr-$volume") { VolumeHeader(volume) }
                    items(vfolders, key = { it.path }) { folder ->
                        FolderListItem(
                            folder          = folder,
                            onClick         = onFolderClick,
                            itemSize        = currentItemSize,
                            isGrid          = false,
                            isScrollingFast = isScrolling
                        )
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
                byVolume.forEach { (volume, vfolders) ->
                    item(key = "hdr-$volume", span = { GridItemSpan(maxLineSpan) }) {
                        VolumeHeader(volume)
                    }
                    gridItems(vfolders, key = { it.path }) { folder ->
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

@Composable
private fun VolumeHeader(volume: String) {
    Text(
        text     = volume.ifEmpty { "Storage" },
        style    = MaterialTheme.typography.titleSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

private fun FolderSort.label() = when (this) {
    FolderSort.NAME       -> "Name"
    FolderSort.DATE       -> "Date"
    FolderSort.SIZE       -> "Count"
    FolderSort.RESOLUTION -> "Resolution"
}

private fun sortComparator(sort: FolderSort): Comparator<FolderNode> = when (sort) {
    FolderSort.NAME       -> compareBy { it.displayName.lowercase() }
    FolderSort.SIZE       -> compareByDescending { it.itemCount }
    FolderSort.DATE       -> compareBy { it.displayName.lowercase() }
    FolderSort.RESOLUTION -> compareBy { it.displayName.lowercase() }
}
