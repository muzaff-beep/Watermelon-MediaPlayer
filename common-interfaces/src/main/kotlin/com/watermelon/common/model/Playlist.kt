package com.watermelon.common.model

/**
 * Represents a playlist — either a system playlist (Recently Added, Favourites)
 * or a user-created personal playlist.
 *
 * System playlists have fixed [id]s and cannot be renamed or deleted.
 */
data class Playlist(
    val id: String,
    val name: String,
    val type: PlaylistType,
    val itemCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val createdAt: Long = 0L
)

enum class PlaylistType {
    RECENTLY_ADDED,      // System — auto-populated, last 7 days
    FAVOURITES,          // System — user manually adds videos
    CONTINUE_WATCHING,   // System — auto-populated from saved (in-progress) resume positions
    USER                 // Personal — user-created
}

object SystemPlaylist {
    const val ID_RECENTLY_ADDED     = "__recently_added__"
    const val ID_FAVOURITES         = "__favourites__"
    const val ID_CONTINUE_WATCHING  = "__continue_watching__"
}