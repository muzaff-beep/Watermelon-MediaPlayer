package com.watermelon.common.model

/**
 * A node in the folder tree built from the MediaStore Phase-1 sweep.
 *
 * @param volume user-facing storage label ("Internal storage" / "SD card") for separating
 *   internal and external storage in the UI. Defaults to "" so existing call sites compile.
 * @param thumbnailUri URI of a representative video in this folder, used to render a folder
 *   thumbnail. Null when unknown.
 */
data class FolderNode(
    val path: String,
    val displayName: String,
    val itemCount: Int,
    val children: List<FolderNode>,
    val volume: String = "",
    val thumbnailUri: String? = null
)
