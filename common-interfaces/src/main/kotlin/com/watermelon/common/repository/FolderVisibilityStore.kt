package com.watermelon.common.repository

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
}
