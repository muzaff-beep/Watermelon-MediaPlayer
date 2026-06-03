package com.watermelon.app

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.watermelon.common.controller.PlaybackController
import com.watermelon.common.repository.FolderRepository
import com.watermelon.common.repository.MediaRepository
import com.watermelon.common.repository.PlaylistRepository
import com.watermelon.common.repository.SubtitleRepository
import com.watermelon.playback.controller.PlaybackControllerImpl
import com.watermelon.storage.db.WatermelonDatabase
import com.watermelon.storage.indexer.MediaStoreIndexer
import com.watermelon.storage.indexer.Phase1Sweep
import com.watermelon.storage.indexer.Phase2Extractor
import com.watermelon.storage.repository.FolderRepositoryImpl
import com.watermelon.storage.repository.MediaRepositoryImpl
import com.watermelon.storage.repository.PlaylistRepositoryImpl
import com.watermelon.subtitle.repository.SubtitleRepositoryImpl
import com.watermelon.ui.screens.FolderBrowserScreen
import com.watermelon.ui.theme.WatermelonTheme
import com.watermelon.ui.viewmodel.FolderViewModel
import java.io.File

/**
 * Single Activity hosting the Compose NavHost. This is the only place concrete
 * implementations are constructed and wired together (Integration Rule 2). Manual
 * constructor injection — no DI framework (Manifest §3).
 */
@UnstableApi
class MainActivity : ComponentActivity() {

    // --- Manual dependency graph (created once per Activity) -------------------------------
    private val database by lazy { WatermelonDatabase(applicationContext) }

    private val indexer by lazy {
        MediaStoreIndexer(
            phase1Sweep = Phase1Sweep(contentResolver),
            phase2Extractor = Phase2Extractor(applicationContext, database),
            mediaUriProvider = { emptyList() } // populated by Phase 1 results in production
        )
    }

    private val mediaRepository: MediaRepository by lazy {
        MediaRepositoryImpl(database, indexer)
    }
    private val folderRepository: FolderRepository by lazy { FolderRepositoryImpl(indexer) }
    private val playlistRepository: PlaylistRepository by lazy { PlaylistRepositoryImpl(database) }
    private val subtitleRepository: SubtitleRepository by lazy {
        SubtitleRepositoryImpl(applicationContext)
    }

    private val exoPlayer by lazy { ExoPlayer.Builder(applicationContext).build() }
    private val playbackController: PlaybackController by lazy {
        PlaybackControllerImpl(applicationContext, exoPlayer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        startFileLogging()
        super.onCreate(savedInstanceState)
        setContent {
            WatermelonTheme {
                val navController = rememberNavController()
                WatermelonNavHost(navController)
            }
        }
    }

    override fun onDestroy() {
        exoPlayer.release()
        super.onDestroy()
    }

    @Composable
    private fun WatermelonNavHost(navController: NavHostController) {
        NavHost(navController = navController, startDestination = Routes.FOLDERS) {
            composable(Routes.FOLDERS) {
                val vm = remember { FolderViewModel(folderRepository, mediaRepository) }
                FolderBrowserScreen(
                    viewModel = vm,
                    onFolderClick = { navController.navigate(Routes.PLAYER) }
                )
            }
            composable(Routes.PLAYER) {
                // PlayerScreen is wired with the controller + a host-provided PlayerSurface in
                // the full integration; route registered here for navigation.
            }
            composable(Routes.SETTINGS) { /* SettingsScreen bound to DataStore */ }
        }
    }

    private fun startFileLogging() {
    val logFile = java.io.File(
        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
        "watermelon_log_${System.currentTimeMillis()}.txt"
    )
    Thread {
        try {
            Thread.sleep(6000)
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            logFile.writeText(process.inputStream.bufferedReader().readText())
        } catch (e: Exception) {
            logFile.writeText("Logger failed: ${e.message}")
        }
    }.start()
}

    private object Routes {
        const val FOLDERS = "folders"
        const val PLAYER = "player"
        const val SETTINGS = "settings"
    }
}
