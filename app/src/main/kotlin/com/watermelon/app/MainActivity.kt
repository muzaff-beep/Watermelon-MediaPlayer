package com.watermelon.app

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.watermelon.common.controller.PlaybackController
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.PlaybackMode
import com.watermelon.common.model.UserIntent
import com.watermelon.common.model.VhsTier
import com.watermelon.common.repository.FolderRepository
import com.watermelon.common.repository.MediaRepository
import com.watermelon.common.repository.PlaylistRepository
import com.watermelon.common.repository.SubtitleRepository
import com.watermelon.playback.controller.PlaybackControllerImpl
import com.watermelon.playback.service.PlaybackConnection
import com.watermelon.storage.db.WatermelonDatabase
import com.watermelon.storage.prefs.FolderVisibilityStoreImpl
import com.watermelon.storage.indexer.MediaStoreIndexer
import com.watermelon.storage.indexer.Phase1Sweep
import com.watermelon.storage.indexer.Phase2Extractor
import com.watermelon.storage.repository.FolderRepositoryImpl
import com.watermelon.storage.repository.MediaRepositoryImpl
import com.watermelon.storage.repository.PlaylistRepositoryImpl
import com.watermelon.subtitle.repository.SubtitleRepositoryImpl
import com.watermelon.ui.screens.FolderBrowserScreen
import com.watermelon.ui.screens.FolderVisibilityScreen
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

@UnstableApi
class MainActivity : ComponentActivity() {

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("watermelon_prefs", Context.MODE_PRIVATE)
    }

    private val database by lazy { WatermelonDatabase(applicationContext) }
    private val settingsStore by lazy { FolderVisibilityStoreImpl(applicationContext) }
    private val phase1Sweep by lazy { Phase1Sweep(contentResolver) }
    private val indexer by lazy {
        MediaStoreIndexer(
            phase1Sweep      = phase1Sweep,
            phase2Extractor  = Phase2Extractor(applicationContext, database),
            mediaUriProvider = { phase1Sweep.lastSweepUris() }
        )
    }
    private val mediaRepository: MediaRepository    by lazy { MediaRepositoryImpl(database, indexer) }
    private val folderRepository: FolderRepository   by lazy { FolderRepositoryImpl(indexer) }
    private val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepositoryImpl(database, mediaRepository)
    }
    @Suppress("unused") private val subtitleRepository: SubtitleRepository by lazy {
        SubtitleRepositoryImpl(applicationContext)
    }

    // Playback is owned by WatermelonPlaybackService. The Activity connects via a
    // MediaController (which implements Player). It is null until connected; Compose
    // recomposes the player screen when it populates.
    private val playbackConnection by lazy { PlaybackConnection(applicationContext) }
    private var mediaController by mutableStateOf<MediaController?>(null)
    private var playbackController: PlaybackController? = null

    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private var permissionsGranted by mutableStateOf(false)
    // Mutual exclusion (issue 9): only one of PIP / BACKGROUND active at a time.
    private var playbackMode by mutableStateOf(PlaybackMode.NORMAL)
    private val isPiPActive: Boolean get() = playbackMode == PlaybackMode.PIP

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

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PiPReceiver.ACTION_PLAY_PAUSE -> {
                    val state = playbackController?.playbackState?.value
                    if (state == PlaybackState.PLAYING) playbackController?.pause()
                    else playbackController?.resume()
                }
                PiPReceiver.ACTION_MUTE -> {
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, if (cur == 0) max / 2 else 0, 0)
                }
                PiPReceiver.ACTION_PREV -> { /* queue not yet implemented */ }
                PiPReceiver.ACTION_NEXT -> { /* queue not yet implemented */ }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installFileLogger()
        installCrashLogger()
        com.watermelon.common.util.FileLogger.i("App", "onCreate — app starting")
        super.onCreate(savedInstanceState)

        // Restore persisted volume
        val savedVolume = prefs.getInt("volume", -1)
        if (savedVolume >= 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
        }

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
        // Connect to the playback service and build the controller once ready.
        playbackConnection.connect { controller ->
            mediaController = controller
            playbackController = PlaybackControllerImpl(applicationContext, controller)
            com.watermelon.common.util.FileLogger.i("App", "playbackController ready from MediaController")
        }
        val filter = IntentFilter().apply {
            addAction(PiPReceiver.ACTION_PLAY_PAUSE)
            addAction(PiPReceiver.ACTION_MUTE)
            addAction(PiPReceiver.ACTION_PREV)
            addAction(PiPReceiver.ACTION_NEXT)
        }
        ContextCompat.registerReceiver(
            this, pipActionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(pipActionReceiver)
        // Persist current volume
        prefs.edit()
            .putInt("volume", audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
            .apply()
        // Keep the controller alive if backgrounding for background/PiP playback;
        // otherwise release it. The service itself keeps the player alive regardless.
        if (playbackMode == PlaybackMode.NORMAL) {
            playbackConnection.release()
            mediaController = null
            playbackController = null
        }
    }

    override fun onDestroy() {
        playbackConnection.release()
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPiPActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPiPMode()
        }
    }

    /**
     * Writes any uncaught exception to a timestamped file in Documents immediately,
     * so crashes can be diagnosed without a PC/adb. Chains to the default handler after.
     */
    /**
     * Wires FileLogger to append standard-level lines to Documents/watermelon.log.
     * Thread-safe via synchronized append.
     */
    private fun installFileLogger() {
        // Prefer public Documents (easy for user to find), but fall back to app-specific
        // external dir which never needs a permission grant. Log the chosen path itself.
        val docsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOCUMENTS
        )
        val primary = java.io.File(docsDir, "watermelon.log")
        val fallback = java.io.File(getExternalFilesDir(null), "watermelon.log")

        val logFile = if (runCatching {
                docsDir.mkdirs()
                java.io.FileWriter(primary, true).use { it.append("") }
                true
            }.getOrDefault(false)) primary else fallback

        runCatching { if (logFile.exists()) logFile.delete() }
        val lock = Any()
        com.watermelon.common.util.FileLogger.install { line ->
            synchronized(lock) {
                runCatching {
                    java.io.FileWriter(logFile, true).use { it.append(line).append('\n') }
                }
            }
        }
        com.watermelon.common.util.FileLogger.i("Log", "log file at: ${logFile.absolutePath}")
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOCUMENTS
                )
                val file = java.io.File(dir, "watermelon_crash_${System.currentTimeMillis()}.txt")
                file.writeText(
                    "Thread: ${thread.name}\n\n" +
                    android.util.Log.getStackTraceString(throwable)
                )
            }
            previous?.uncaughtException(thread, throwable)
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

        val isPlaying = playbackController?.playbackState?.value == PlaybackState.PLAYING
        val ppIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        actions += makeAction(PiPReceiver.ACTION_PLAY_PAUSE, ppIcon, if (isPlaying) "Pause" else "Play")
        actions += makeAction(PiPReceiver.ACTION_PREV, android.R.drawable.ic_media_previous, "Previous")
        actions += makeAction(PiPReceiver.ACTION_NEXT, android.R.drawable.ic_media_next, "Next")

        // Get video dimensions for dynamic aspect ratio
        val videoWidth  = mediaController?.videoSize?.width ?: 16
        val videoHeight = mediaController?.videoSize?.height ?: 9
        val rational = if (videoWidth > 0 && videoHeight > 0)
            Rational(videoWidth, videoHeight) else Rational(16, 9)

        val params = PictureInPictureParams.Builder()
            .setAspectRatio(rational)
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

        // Restore persisted brightness
        val savedBrightness = remember { prefs.getFloat("brightness", -1f) }

        NavHost(navController = navController, startDestination = Routes.FOLDERS) {
            composable(Routes.FOLDERS) {
                val vm = remember {
                    FolderViewModel(folderRepository, mediaRepository, playlistRepository, settingsStore)
                }
                FolderBrowserScreen(
                    viewModel     = vm,
                    onFolderClick = { folder ->
                        if (folder.isPlaylist) {
                            navController.navigate("videos/${Uri.encode(folder.playlistId!!)}")
                        } else {
                            navController.navigate("videos/${Uri.encode(folder.path)}")
                        }
                    },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) }
                )
            }
            composable(
                route     = "videos/{folderPath}",
                arguments = listOf(navArgument("folderPath") { type = NavType.StringType })
            ) { backStackEntry ->
                val folderPath = Uri.decode(backStackEntry.arguments?.getString("folderPath").orEmpty())
                val vm = remember(folderPath) {
                    VideoListViewModel(mediaRepository, folderPath, playlistRepository)
                }
                val playlists by playlistRepository.observeAll()
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                VideoListScreen(
                    viewModel          = vm,
                    onVideoClick       = { item -> navController.navigate("player/${Uri.encode(item.uri)}") },
                    availablePlaylists = playlists
                )
            }
            composable(
                route     = "player/{uri}",
                arguments = listOf(navArgument("uri") { type = NavType.StringType })
            ) { backStackEntry ->
                val mediaUri = Uri.decode(backStackEntry.arguments?.getString("uri").orEmpty())
                val controller = mediaController
                val pbController = playbackController
                if (controller == null || pbController == null) {
                    // Controller still connecting — brief loading state.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Connecting…", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    val vm = remember(pbController) { PlayerViewModel(pbController) }
                    LaunchedEffect(mediaUri) { vm.onIntent(UserIntent.Play(mediaUri)) }
                    PlayerScreen(
                        viewModel       = vm,
                        onBack          = { navController.popBackStack() },
                        vhsTier         = VhsTier.C,
                        vhsIntensity    = 0.5f,
                        durationMs      = controller.duration.coerceAtLeast(0L),
                        currentSubtitle = null,
                        uri             = mediaUri,
                        screenshotMode  = settingsState.screenshotMode,
                        initialBrightness = savedBrightness,
                        onPipClick      = {
                            // Mutual exclusion: PiP disables background.
                            playbackMode = PlaybackMode.PIP
                            enterPiPMode()
                        },
                        onBackgroundClick = { enabled ->
                            playbackMode = if (enabled) PlaybackMode.BACKGROUND else PlaybackMode.NORMAL
                            // The service already owns playback; background "enable" just means
                            // we allow the Activity to stop without tearing down the controller.
                            // No separate startService needed — MediaController started the session.
                        },
                        onBrightnessChange = { brightness ->
                            prefs.edit().putFloat("brightness", brightness).apply()
                        },
                        surface         = { modifier ->
                            AndroidView(
                                modifier = modifier,
                                factory  = { ctx ->
                                    PlayerView(ctx).apply {
                                        player        = controller
                                        useController = false
                                    }
                                },
                                update = { it.player = controller }
                            )
                        }
                    )
                }
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    state                   = settingsState,
                    onStateChange           = { settingsState = it },
                    onFolderVisibilityClick = { navController.navigate(Routes.FOLDER_VISIBILITY) }
                )
            }
            composable(Routes.FOLDER_VISIBILITY) {
                val vm = remember {
                    FolderViewModel(folderRepository, mediaRepository, playlistRepository, settingsStore)
                }
                val folders by vm.allFoldersForSettings.collectAsStateWithLifecycle()
                FolderVisibilityScreen(
                    folders  = folders
                        .filter { !it.isPlaylist }
                        .map { Triple(it.path, it.displayName, vm.isFolderVisible(it.path)) },
                    onToggle = { path, visible -> vm.setFolderHidden(path, !visible) }
                )
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

    private object Routes {
        const val FOLDERS           = "folders"
        const val SETTINGS          = "settings"
        const val FOLDER_VISIBILITY = "folder_visibility"
    }
}
