package com.watermelon.common.model

/**
 * Represents the current multi-select state in a video list.
 * Empty [selectedUris] means selection mode is inactive.
 */
data class SelectionState(
    val selectedUris: Set<String> = emptySet()
) {
    val isActive: Boolean get() = selectedUris.isNotEmpty()
    val count: Int get() = selectedUris.size

    fun toggle(uri: String): SelectionState = copy(
        selectedUris = if (uri in selectedUris) selectedUris - uri else selectedUris + uri
    )

    fun selectAll(uris: List<String>): SelectionState = copy(selectedUris = uris.toSet())
    fun clear(): SelectionState = copy(selectedUris = emptySet())
    fun contains(uri: String): Boolean = uri in selectedUris
}
