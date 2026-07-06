package com.watermelon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

/**
 * Watermelon MediaPlayer bottom navigation bar.
 * Implements the "Bottom navigation" requirement from the UI Design System.
 */
enum class BottomNavItem(
    val route: String,
    val iconRes: Int,
    val selectedIconRes: Int,
    val label: String
) {
    FOLDERS(
        route = "folders",
        iconRes = R.drawable.ic_folder_outline,
        selectedIconRes = R.drawable.ic_folder_filled,
        label = "Folders"
    ),
    VIDEOS(
        route = "videos",
        iconRes = R.drawable.ic_video_outline,
        selectedIconRes = R.drawable.ic_video_filled,
        label = "Videos"
    ),
    PLAYLISTS(
        route = "playlists",
        iconRes = R.drawable.ic_playlist_outline,
        selectedIconRes = R.drawable.ic_playlist_filled,
        label = "Playlists"
    ),
    FAVORITES(
        route = "favorites",
        iconRes = R.drawable.ic_star_outline,
        selectedIconRes = R.drawable.ic_star_filled,
        label = "Favorites"
    ),
    SETTINGS(
        route = "settings",
        iconRes = R.drawable.ic_settings_outline,
        selectedIconRes = R.drawable.ic_settings_filled,
        label = "Settings"
    )
}

@Composable
fun WatermelonBottomNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem.FOLDERS,
        BottomNavItem.VIDEOS,
        BottomNavItem.PLAYLISTS,
        BottomNavItem.FAVORITES,
        BottomNavItem.SETTINGS
    )

    val currentDestination = navController.currentBackStackEntry?.destination

    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(WatermelonColors.DarkSurface),
        containerColor = WatermelonColors.DarkSurface,
        contentColor = WatermelonColors.DarkOnSurface
    ) {
        items.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        // on the back stack as users select items
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(
                            if (selected) item.selectedIconRes else item.iconRes
                        ),
                        contentDescription = item.label,
                        tint = if (selected) WatermelonColors.Accent else WatermelonColors.DarkOnSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = WatermelonTypography.typography.labelSmall,
                        color = if (selected) WatermelonColors.Accent else WatermelonColors.DarkOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = WatermelonColors.Accent,
                    unselectedIconColor = WatermelonColors.DarkOnSurfaceVariant,
                    selectedTextColor = WatermelonColors.Accent,
                    unselectedTextColor = WatermelonColors.DarkOnSurfaceVariant,
                    indicatorColor = WatermelonColors.Accent.copy(alpha = 0.12f)
                )
            )
        }
    }
}

@Preview
@Composable
private fun WatermelonBottomNavigationPreview() {
    // Preview requires a NavController, which we can't easily provide in a preview
    // This is a limitation of the preview system
    WatermelonBottomNavigation(
        navController = NavController(LocalContext.current)
    )
}