package com.watermelon.ui.screens

import androidx.compose.runtime.mutableStateOf

/**
 * Holds the ordered list of sibling video URIs for the currently-launched playback session,
 * so the player can offer next/previous-track navigation without re-querying the repository.
 *
 * Architecture (a): the launching screen (video list / folder) sets [uris] in the exact
 * displayed order right before navigating to the player. The player reads it to resolve
 * adjacency. Cleared/replaced on each new launch.
 *
 * This is a lightweight in-memory holder — playback is a single foreground session, so a
 * process-wide holder is sufficient and avoids encoding a whole list into a nav route.
 */
object PlaybackQueue {
    /** Ordered video URIs as shown in the launching list. */
    var uris: List<String> = emptyList()
        private set

    fun set(list: List<String>) { uris = list }

    fun indexOf(uri: String): Int = uris.indexOf(uri)

    /** URI of the next track after [currentUri], or null if none (last / not found / lone). */
    fun nextOf(currentUri: String): String? {
        val i = uris.indexOf(currentUri)
        return if (i in 0 until uris.lastIndex) uris[i + 1] else null
    }

    /** URI of the previous track before [currentUri], or null if none (first / not found). */
    fun previousOf(currentUri: String): String? {
        val i = uris.indexOf(currentUri)
        return if (i > 0) uris[i - 1] else null
    }
}
