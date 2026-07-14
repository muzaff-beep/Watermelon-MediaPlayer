package com.watermelon.storage.prefs

import android.content.Context
import android.content.SharedPreferences
import com.watermelon.common.repository.FolderVisibilityStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lightweight persistence for user settings that must survive app restarts.
 * Backed by SharedPreferences. Used for folder visibility (Issue 16) and any other
 * simple key-value preferences.
 *
 * Folder visibility model: we store the SET OF HIDDEN folder paths. A folder is visible
 * unless its path is in the hidden set. This keeps newly-discovered folders visible by
 * default without needing to write an entry for every folder.
 */
class FolderVisibilityStoreImpl(context: Context) : FolderVisibilityStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("watermelon_settings", Context.MODE_PRIVATE)

    // ── Folder visibility ──────────────────────────────────────────────────────

    private val _visibilityVersion = MutableStateFlow(0)
    override val visibilityVersion: StateFlow<Int> = _visibilityVersion

    override fun getHiddenFolders(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN_FOLDERS, emptySet()) ?: emptySet()

    override fun setFolderHidden(path: String, hidden: Boolean) {
        val current = getHiddenFolders().toMutableSet()
        if (hidden) current.add(path) else current.remove(path)
        prefs.edit().putStringSet(KEY_HIDDEN_FOLDERS, current).apply()
        _visibilityVersion.value += 1
    }

    override fun isFolderVisible(path: String): Boolean = path !in getHiddenFolders()

    // ── Generic helpers (brightness, volume already handled in MainActivity prefs) ─

    fun getString(key: String, default: String): String =
        prefs.getString(key, default) ?: default

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    companion object {
        private const val KEY_HIDDEN_FOLDERS = "hidden_folders"
    }
}
