package com.watermelon.common.model

/**
 * A node in the folder tree built from the MediaStore Phase-1 sweep,
 * or a virtual node representing a system/user playlist.
 *
 * @param volume user-facing storage label ("Internal storage" / "SD card") for separating
 *   internal and external storage in the UI. Empty for playlist nodes.
 * @param thumbnailUri URI of a representative video, used to render a folder thumbnail.
 * @param playlistId non-null when this node represents a playlist rather than a real folder.
 * @param playlistType type of playlist if this is a playlist node.
 */
data class FolderNode(
    val path: String,
    val displayName: String,
    val itemCount: Int,
    val children: List<FolderNode>,
    val volume: String = "",
    val thumbnailUri: String? = null,
    val totalDurationMs: Long = 0L,
    val hasNewFiles: Boolean = false,
    val playlistId: String? = null,
    val playlistType: PlaylistType? = null
) {
    val isPlaylist: Boolean get() = playlistId != null
    val isSystemPlaylist: Boolean get() =
        playlistType == PlaylistType.RECENTLY_ADDED ||
        playlistType == PlaylistType.FAVOURITES ||
        playlistType == PlaylistType.CONTINUE_WATCHING
}
