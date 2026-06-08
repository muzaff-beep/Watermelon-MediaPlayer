package com.watermelon.app

import android.Manifest
import android.app.PictureInPictureParams
import android.app.PendingIntent
import android.app.RemoteAction
import android.graphics.drawable.Icon
import android.util.Rational
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.watermelon.common.controller.PlaybackController
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.UserIntent
import com.watermelon.common.model.VhsTier
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
import com.watermelon.ui.screens.PlayerScreen
import com.watermelon.ui.screens.ScreenshotMode
import com.watermelon.ui.screens.SettingsScreen
import com.watermelon.ui.screens.SettingsState
import com.watermelon.ui.screens.VideoListScreen
import com.watermelon.ui.theme.WatermelonTheme
import com.watermelon.ui.viewmodel.FolderViewModel
import com.watermelon.ui.viewmodel.PlayerViewModel
import com.watermelon.ui.viewmodel.VideoListViewModel
import kotlinx.coroutines.launch

/**
 * Single Activity hosting the Compose NavHost. Manual constructor injection — no DI framework
 * (Manifest §3). Also handles PiP lifecycle: registers [PiPReceiver] to forward mini-control
 * actions to the [PlaybackController].
 */
@UnstableApi
class MainActivity : ComponentActivity() {

    private val database by lazy { WatermelonDatabase(applicationContext) }
    private val phase1Sweep by lazy { Phase1Sweep(contentResolver) }
    private val indexer by lazy {
        MediaStoreIndexer(
            phase1Sweep      = phase1Sweep,
            phase2Extractor  = Phase2Extractor(applicationContext, database),
            mediaUriProvider = { phase1Sweep.lastSweepUris() }
        )
    }
    private val mediaRepository: MediaRepository   by lazy { MediaRepositoryImpl(database, indexer) }
    private val folderRepository: FolderRepository  by lazy { FolderRepositoryImpl(indexer) }
    @Suppress("unused") private val playlistRepository: PlaylistRepository by lazy { PlaylistRepositoryImpl(database) }
    @Suppress("unused") private val subtitleRepository: SubtitleRepository by lazy { SubtitleRepositoryImpl(applicationContext) }

    private val exoPlayer by lazy { ExoPlayer.Builder(applicationContext).build() }
    private val playbackController: PlaybackController by lazy {
        PlaybackControllerImpl(applicationContext, exoPlayer)
    }

    private var permissionsGranted by mutableStateOf(false)
    private var isPiPActive = false  // tracks whether we're in PiP mode

    private val requiredPermissions: Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (permissionsGranted) triggerInitialIndex()
    }

    // PiP: forward broadcast actions to the controller.
    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PiPReceiver.ACTION_PLAY_PAUSE -> {
                    val state = playbackController.playbackState.value
                    if (state == PlaybackState.PLAYING) playbackController.pause()
                    else playbackController.resume()
                }
                PiPReceiver.ACTION_MUTE -> {
                    val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
                    val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    val cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, if (cur == 0) max / 2 else 0, 0)
                }
                PiPReceiver.ACTION_PREV -> { /* queue not yet implemented */ }
                PiPReceiver.ACTION_NEXT -> { /* queue not yet implemented */ }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        startFileLogging()
        super.onCreate(savedInstanceState)

        permissionsGranted = requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (permissionsGranted) triggerInitialIndex()
        else permissionLauncher.launch(requiredPermissions)

        setContent {
            WatermelonTheme {
                val navController = rememberNavController()
                if (permissionsGranted) WatermelonNavHost(navController)
                else PermissionPrompt(onRequest = { permissionLauncher.launch(requiredPermissions) })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(PiPReceiver.ACTION_PLAY_PAUSE)
            addAction(PiPReceiver.ACTION_MUTE)
            addAction(PiPReceiver.ACTION_PREV)
            addAction(PiPReceiver.ACTION_NEXT)
        }
        registerReceiver(pipActionReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(pipActionReceiver)
    }

    override fun onDestroy() {
        exoPlayer.release()
        super.onDestroy()
    }

    /** Auto-enter PiP if PiP is currently enabled when user presses Home. */
    override fun onUserLeaveHint() {
        if (isPiPActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    private fun triggerInitialIndex() {
        lifecycleScope.launch { mediaRepository.refreshIndex() }
    }

    private fun enterPiPMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val actions = mutableListOf<android.app.RemoteAction>()

        fun makeAction(action: String, iconRes: Int, title: String): android.app.RemoteAction {
            val intent = PendingIntent.getBroadcast(
                this@MainActivity, action.hashCode(),
                Intent(action).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return android.app.RemoteAction(
                Icon.createWithResource(this@MainActivity, iconRes),
                title, title, intent
            )
        }

        val state = playbackController.playbackState.value
        val isPlaying = state == PlaybackState.PLAYING
        val ppIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        actions += makeAction(PiPReceiver.ACTION_PLAY_PAUSE, ppIcon, if (isPlaying) "Pause" else "Play")
        actions += makeAction(PiPReceiver.ACTION_PREV, android.R.drawable.ic_media_previous, "Previous")
        actions += makeAction(PiPReceiver.ACTION_NEXT, android.R.drawable.ic_media_next, "Next")

        val params = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(actions)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(false)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
        enterPictureInPictureMode(params)
    }

    @Composable
    private fun WatermelonNavHost(navController: NavHostController) {
        var settingsState by remember { mutableStateOf(SettingsState()) }

        NavHost(navController = navController, startDestination = Routes.FOLDERS) {
            composable(Routes.FOLDERS) {
                val vm = remember { FolderViewModel(folderRepository, mediaRepository) }
                FolderBrowserScreen(
                    viewModel     = vm,
                    onFolderClick = { folder -> navController.navigate("videos/${Uri.encode(folder.path)}") }
                )
            }
            composable(
                route     = "videos/{folderPath}",
                arguments = listOf(navArgument("folderPath") { type = NavType.StringType })
            ) { backStackEntry ->
                val folderPath = Uri.decode(backStackEntry.arguments?.getString("folderPath").orEmpty())
                val vm = remember(folderPath) { VideoListViewModel(mediaRepository, folderPath) }
                VideoListScreen(
                    viewModel    = vm,
                    onVideoClick = { item -> navController.navigate("player/${Uri.encode(item.uri)}") }
                )
            }
            composable(
                route     = "player/{uri}",
                arguments = listOf(navArgument("uri") { type = NavType.StringType })
            ) { backStackEntry ->
                val mediaUri = Uri.decode(backStackEntry.arguments?.getString("uri").orEmpty())
                val vm = remember { PlayerViewModel(playbackController) }
                LaunchedEffect(mediaUri) { vm.onIntent(UserIntent.Play(mediaUri)) }
                PlayerScreen(
                    viewModel    = vm,
                    onBack       = { navController.popBackStack() },
                    vhsTier      = VhsTier.C,
                    vhsIntensity = 0.5f,
                    durationMs   = exoPlayer.duration.coerceAtLeast(0L),
                    currentSubtitle = null,
                    uri          = mediaUri,
                    screenshotMode = settingsState.screenshotMode,
                    onPipClick   = { isPiPActive = true; enterPiPMode() },
                    onBackgroundClick = { enabled ->
                        if (enabled) {
                            startService(Intent(this@MainActivity, com.watermelon.playback.service.WatermelonPlaybackService::class.java))
                        } else {
                            stopService(Intent(this@MainActivity, com.watermelon.playback.service.WatermelonPlaybackService::class.java))
                        }
                    },
                    surface      = { modifier ->
                        AndroidView(
                            modifier = modifier,
                            factory  = { ctx ->
                                PlayerView(ctx).apply {
                                    player        = exoPlayer
                                    useController = false   // custom controls only
                                }
                            }
                        )
                    }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(state = settingsState, onStateChange = { settingsState = it })
            }
        }
    }

    @Composable
    private fun PermissionPrompt(onRequest: () -> Unit) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier            = Modifier.padding(24.dp)
            ) {
                Text(
                    "Watermelon needs access to your videos to build the library.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(onClick = onRequest) { Text("Grant access") }
            }
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
        const val FOLDERS  = "folders"
        const val SETTINGS = "settings"
    }
}
