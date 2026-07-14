package com.watermelon.common.model

/** A subtitle candidate returned by the OpenSubtitles lookup. */
data class SubtitleTrack(
    val language: String,
    val label: String,
    val downloadUrl: String,
    val rating: Float
)
