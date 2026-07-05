package com.watermelon.common.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction for persisting folder-visibility preferences. Lives in common-interfaces
 * so ViewModels (ui-presentation) can depend on it without reaching into library-storage.
 *
 * Visibility model: a folder is visible unless its path is in the hidden set, so newly
 * discovered folders are visible by default.
 */
interface FolderVisibilityStore {
    fun getHiddenFolders(): Set<String>
    fun isFolderVisible(path: String): Boolean
    fun setFolderHidden(path: String, hidden: Boolean)

    /**
     * Bumped every time [setFolderHidden] changes the hidden set. Any Flow that depends on
     * folder visibility (e.g. PlaylistRepositoryImpl's Recently Added / Continue Watching /
     * Favourites) should combine on this so hiding or re-enabling a folder is reflected
     * immediately, rather than only on the next unrelated media-list change.
     */
    val visibilityVersion: StateFlow<Int>
}
