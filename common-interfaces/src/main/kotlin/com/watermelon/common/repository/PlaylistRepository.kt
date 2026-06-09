package com.watermelon.common.repository

import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.Playlist
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    /** All playlists in display order: Recently Added, Favourites, then user playlists. */
    fun observeAll(): Flow<List<Playlist>>

    /** Videos inside a specific playlist. */
    fun observeVideos(playlistId: String): Flow<List<MediaItem>>

    /** Add a video to Favourites. */
    suspend fun addToFavourites(uri: String)

    /** Remove a video from Favourites. */
    suspend fun removeFromFavourites(uri: String)

    /** Check if a video is in Favourites. */
    suspend fun isFavourite(uri: String): Boolean

    /** Create a new user playlist. Returns the new playlist id. */
    suspend fun createPlaylist(name: String): String

    /** Delete a user playlist. System playlists cannot be deleted. */
    suspend fun deletePlaylist(playlistId: String)

    /** Rename a user playlist. */
    suspend fun renamePlaylist(playlistId: String, newName: String)

    /** Add a video to a user playlist. */
    suspend fun addToPlaylist(playlistId: String, uri: String)

    /** Remove a video from a user playlist. */
    suspend fun removeFromPlaylist(playlistId: String, uri: String)
}
