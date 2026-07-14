package com.watermelon.subtitle.network

/**
 * Maintains an ordered list of API mirrors with failover (Manifest §6.2 / Teams §5).
 * On a 5xx response or timeout the caller invokes [advance] to move to the next mirror.
 * The rotation is fail-closed: once every mirror has been exhausted, [hasNext] is false and
 * the lookup gives up rather than falling back to an insecure path.
 */
class MirrorRotator(
    private val mirrors: List<String>
) {
    init { require(mirrors.isNotEmpty()) { "At least one mirror is required" } }

    private var index = 0

    /** The mirror currently in use. */
    val current: String
        get() = mirrors[index]

    /** True while there is another mirror to fail over to. */
    fun hasNext(): Boolean = index < mirrors.lastIndex

    /** Advance to the next mirror; returns it, or null if the list is exhausted. */
    fun advance(): String? {
        if (!hasNext()) return null
        index++
        return current
    }

    /** Reset to the primary mirror (e.g. at the start of a new lookup). */
    fun reset() { index = 0 }

    companion object {
        /** Default OpenSubtitles mirror order (primary first). */
        val DEFAULT = MirrorRotator(
            listOf(
                "https://api.opensubtitles.com",
                "https://rest.opensubtitles.org",
                "https://www.opensubtitles.org"
            )
        )
    }
}
