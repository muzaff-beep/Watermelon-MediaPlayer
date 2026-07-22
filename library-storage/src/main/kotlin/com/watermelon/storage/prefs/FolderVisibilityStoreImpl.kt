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

    // ── media-tools output paths (compressed/trimmed video destinations) ──────────
    // User-configurable per product requirement. Values are MediaStore RELATIVE_PATH
    // strings (e.g. "Movies/Watermelon/compressed") -- see OutputFileStore for how
    // they're consumed, and its note on the API < 29 RELATIVE_PATH limitation.

    fun getCompressedOutputPath(): String = getString(KEY_COMPRESSED_PATH, "Movies/Watermelon/compressed")
    fun setCompressedOutputPath(path: String) = putString(KEY_COMPRESSED_PATH, path)

    fun getTrimmedOutputPath(): String = getString(KEY_TRIMMED_PATH, "Movies/Watermelon/trimmed")
    fun setTrimmedOutputPath(path: String) = putString(KEY_TRIMMED_PATH, path)

    companion object {
        private const val KEY_HIDDEN_FOLDERS = "hidden_folders"
        private const val KEY_COMPRESSED_PATH = "media_tools_compressed_path"
        private const val KEY_TRIMMED_PATH = "media_tools_trimmed_path"
    }
}
