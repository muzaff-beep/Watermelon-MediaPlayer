package com.watermelon.common.model

/**
 * Canonical MVI intent surface. UI emits these; ViewModels translate them into
 * repository/controller calls.
 */
sealed class UserIntent {
    data class Play(val uri: String) : UserIntent()
    data class Seek(val positionMs: Long) : UserIntent()
    data class SetSpeed(val speed: Float) : UserIntent()
    data object Pause : UserIntent()
    data object Resume : UserIntent()
    data object RefreshLibrary : UserIntent()
}
