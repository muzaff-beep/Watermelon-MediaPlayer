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
import androidx.annotation.RequiresApi
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.watermelon.common.controller.PlaybackController
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.PlaybackMode
import com.watermelon.common.model.UserIntent
import com.watermelon.common.repository.FolderRepository
import com.watermelon.common.repository.MediaRepository
import com.watermelon.common.repository.PlaylistRepository
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
import com.watermelon.ui.components.WatermelonBottomNavigation
import com.watermelon.ui.components.BottomNavItem
import com.watermelon.ui.screens.DesignSystemScreen
import com.watermelon.ui.screens.FolderBrowserScreen
import com.watermelon.ui.screens.FolderVisibilityScreen
import com.watermelon.ui.screens.PhonePlayerScreen
import com.watermelon.ui.screens.PlaylistsScreen
import com.watermelon.ui.screens.ScreenshotMode
import com.watermelon.ui.screens.SettingsScreen
import com.watermelon.ui.screens.SettingsState
import com.watermelon.ui.screens.VideoListScreen
import com.watermelon.ui.theme.WatermelonTheme
import com.watermelon.ui.viewmodel.FolderViewModel
import com.watermelon.ui.viewmodel.PlayerViewModel
import com.watermelon.ui.viewmodel.PlaylistViewModel
import com.watermelon.ui.viewmodel.VideoListViewModel
import kotlinx.coroutines.launch

@UnstableApi
class MainActivity : ComponentActivity() {

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("watermelon_prefs", Context.MODE_PRIVATE)
    }

    private val database by lazy { WatermelonDatabase(applicationContext) }
    private val settingsStore by lazy { FolderVisibilityStoreImpl(applicationContext) }
    private val vhsReverseSound by lazy { VhsReverseSound() }
    private val subtitleRepository by lazy {
        com.watermelon.subtitle.repository.SubtitleRepositoryImpl(applicationContext)
    }
    private val phase1Sweep by lazy { Phase1Sweep(contentResolver) }
    private val indexer by lazy {
        MediaStoreIndexer(
            phase1Sweep = phase1Sweep,
            phase2Extractor = Phase2Extractor(applicationContext, database),
            mediaUriProvider = { phase1Sweep.lastSweepUris() }
        )
    }
    private val mediaRepository: MediaRepository by lazy { MediaRepositoryImpl(database, indexer) }
    private val folderRepository: FolderRepository by lazy { FolderRepositoryImpl(indexer) }
    private val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepositoryImpl(database, mediaRepository, settingsStore)
    }
    private val playbackPositionRepository by lazy {
        com.watermelon.storage.repository.PlaybackPositionRepositoryImpl(database)
    }

    private val playbackConnection by lazy { PlaybackConnection(applicationContext) }
    private var mediaController by mutableStateOf<MediaController?>(null)
    private var playbackController: PlaybackController? = null

    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private var permissionsGranted by mutableStateOf(false)
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
            val controller = playbackController ?: return
            when (intent.action) {
                PiPReceiver.ACTION_PLAY_PAUSE -> {
                    val state = controller.playbackState.value
                    if (state == PlaybackState.PLAYING) controller.pause()
                    else controller.resume()
                }
                PiPReceiver.ACTION_MUTE -> {
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, if (cur == 0) max / 2 else 0, 0)
                }
                PiPReceiver.ACTION_PREV -> seekRelative(controller, -30_000)
                PiPReceiver.ACTION_NEXT -> seekRelative(controller, +30_000)
                PiPReceiver.ACTION_REWIND -> seekRelative(controller, -10_000)
                PiPReceiver.ACTION_FORWARD -> seekRelative(controller, +10_000)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPiPActive) {
                val tier = tierForWidth(resources.configuration.screenWidthDp)
                setPictureInPictureParams(buildPiPParams(tier))
            }
        }
    }

    private fun seekRelative(controller: PlaybackController, deltaMs: Long) {
        val pos = controller.currentPositionMs.value
        controller.seekTo((pos + deltaMs).coerceAtLeast(0))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installFileLogger()
        installCrashLogger()
        com.watermelon.common.util.FileLogger.i("App", "onCreate — app starting")
        super.onCreate(savedInstanceState)

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
            var pureDarkTheme by remember {
                mutableStateOf(prefs.getBoolean("pure_dark", true))
            }

            WatermelonTheme(darkTheme = pureDarkTheme) {
                val navController = rememberNavController()

                // Track current destination for bottom navigation
                val currentDestination = navController.currentBackStackEntryAsState().value?.destination

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (shouldShowBottomBar(currentDestination)) {
                            WatermelonBottomNavigation(
                                navController = navController,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                ) { innerPadding ->
                    if (permissionsGranted) {
                        WatermelonNavHost(
                            navController = navController,
                            pureDarkTheme = pureDarkTheme,
                            onPureDarkThemeChange = { enabled ->
                                pureDarkTheme = enabled
                                prefs.edit().putBoolean("pure_dark", enabled).apply()
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        PermissionPrompt(onRequest = { permissionLauncher.launch(requiredPermissions) })
                    }
                }
            }
        }
    }

    /**
     * Determine if bottom navigation bar should be shown.
     * Hide for full-screen player and PiP mode.
     */
    private fun shouldShowBottomBar(destination: NavDestination?): Boolean {
        return destination?.route != "player/{uri}" && !isPiPActive
    }

    override fun onStart() {
        super.onStart()
        if (playbackController == null) {
            playbackConnection.connect { controller ->
                mediaController = controller
                playbackController = PlaybackControllerImpl(
                    context = applicationContext,
                    player = controller,
                    positionRepository = playbackPositionRepository
                )
                com.watermelon.common.util.FileLogger.i("App", "playbackController ready from MediaController")
            }
        } else {
            com.watermelon.common.util.FileLogger.i("App", "onStart — controller already live, reusing")
        }
        val filter = IntentFilter().apply {
            addAction(PiPReceiver.ACTION_PLAY_PAUSE)
            addAction(PiPReceiver.ACTION_MUTE)
            addAction(PiPReceiver.ACTION_PREV)
            addAction(PiPReceiver.ACTION_NEXT)
            addAction(PiPReceiver.ACTION_REWIND)
            addAction(PiPReceiver.ACTION_FORWARD)
        }
        ContextCompat.registerReceiver(
            this, pipActionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(pipActionReceiver)
        prefs.edit()
            .putInt("volume", audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
            .apply()
        if (playbackMode == PlaybackMode.NORMAL) {
            (playbackController as? PlaybackControllerImpl)?.release()
            playbackConnection.release()
            mediaController = null
            playbackController = null
        }
    }

    override fun onDestroy() {
        vhsReverseSound.stop()
        playbackConnection.release()
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        com.watermelon.common.util.FileLogger.i("PiP",
            "onUserLeaveHint — isPiPActive=$isPiPActive mode=$playbackMode")
        val controller = playbackController
        val isPlaying = controller?.playbackState?.value == PlaybackState.PLAYING
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            playbackMode == PlaybackMode.NORMAL && isPlaying
        ) {
            playbackMode = PlaybackMode.PIP
            enterPiPMode()
        } else if (isPiPActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPiPMode()
        }
    }

    private fun installFileLogger() {
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun enterPiPMode() {
        com.watermelon.common.util.FileLogger.i("PiP", "enterPiPMode called — entering now")
        val ok = enterPictureInPictureMode(buildPiPParams(PiPTier.MID))
        com.watermelon.common.util.FileLogger.i("PiP", "enterPictureInPictureMode returned $ok")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun makePiPAction(action: String, iconRes: Int, title: String): android.app.RemoteAction {
        val intent = PendingIntent.getBroadcast(
            this, action.hashCode(),
            Intent(action).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return android.app.RemoteAction(
            Icon.createWithResource(this, iconRes), title, title, intent
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPiPActions(tier: PiPTier): List<android.app.RemoteAction> {
        val isPlaying = playbackController?.playbackState?.value == PlaybackState.PLAYING
        val ppIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPause = makePiPAction(PiPReceiver.ACTION_PLAY_PAUSE, ppIcon, if (isPlaying) "Pause" else "Play")
        val prev = makePiPAction(PiPReceiver.ACTION_PREV, android.R.drawable.ic_media_previous, "Previous")
        val next = makePiPAction(PiPReceiver.ACTION_NEXT, android.R.drawable.ic_media_next, "Next")
        val rew = makePiPAction(PiPReceiver.ACTION_REWIND, android.R.drawable.ic_media_rew, "Rewind 10s")
        val fwd = makePiPAction(PiPReceiver.ACTION_FORWARD, android.R.drawable.ic_media_ff, "Forward 10s")
        return when (tier) {
            PiPTier.SMALL -> listOf(playPause)
            PiPTier.MID -> listOf(prev, playPause, next)
            PiPTier.EXPANDED -> listOf(rew, prev, playPause, next, fwd)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPiPParams(tier: PiPTier): PictureInPictureParams {
        val videoWidth = mediaController?.videoSize?.width ?: 16
        val videoHeight = mediaController?.videoSize?.height ?: 9
        val rational = if (videoWidth > 0 && videoHeight > 0)
            Rational(videoWidth, videoHeight) else Rational(16, 9)
        return PictureInPictureParams.Builder()
            .setAspectRatio(rational)
            .setActions(buildPiPActions(tier))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(false)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
    }

    private fun tierForWidth(widthDp: Int): PiPTier = when {
        widthDp < 200 -> PiPTier.SMALL
        widthDp < 400 -> PiPTier.MID
        else -> PiPTier.EXPANDED
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val tier = tierForWidth(newConfig.screenWidthDp)
            com.watermelon.common.util.FileLogger.i("PiP",
                "size change: width=${newConfig.screenWidthDp}dp -> tier=$tier")
            setPictureInPictureParams(buildPiPParams(tier))
        } else {
            com.watermelon.common.util.FileLogger.i("PiP",
                "exited PiP — resetting playbackMode to NORMAL")
            playbackMode = PlaybackMode.NORMAL
        }
    }

    @Composable
    private fun WatermelonNavHost(
        navController: NavHostController,
        pureDarkTheme: Boolean,
        onPureDarkThemeChange: (Boolean) -> Unit,
        modifier: Modifier = Modifier
    ) {
        var settingsState by remember {
            mutableStateOf(
                SettingsState(
                    pureDark = pureDarkTheme,
                    vhsEnabled = prefs.getBoolean("vhs_enabled", true),
                    vhsIntensity = runCatching {
                        com.watermelon.ui.screens.VhsIntensity.valueOf(
                            prefs.getString("vhs_intensity", "MED") ?: "MED"
                        )
                    }.getOrDefault(com.watermelon.ui.screens.VhsIntensity.MED),
                    tunerSeekBarEnabled = prefs.getBoolean("tuner_seekbar_enabled", true)
                )
            )
        }

        val savedBrightness = remember { prefs.getFloat("brightness", -1f) }

        NavHost(
            navController = navController,
            startDestination = Routes.FOLDERS,
            modifier = modifier
        ) {
            composable(Routes.FOLDERS) {
                val vm = remember {
                    FolderViewModel(folderRepository, mediaRepository, playlistRepository, settingsStore)
                }
                val onFolderClick: (com.watermelon.common.model.FolderNode) -> Unit = { folder ->
                    if (folder.isPlaylist) {
                        navController.navigate("videos/${Uri.encode(folder.playlistId!!)}?isPlaylist=true")
                    } else {
                        navController.navigate("videos/${Uri.encode(folder.path)}?isPlaylist=false")
                    }
                }
                val isTelevision = remember {
                    com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(this@MainActivity)
                }
                if (isTelevision) {
                    // D-pad-navigable row list with visible focus rings — no grid/sort/filter
                    // chrome, which are touch affordances that don't map to a 10-foot D-pad UI.
                    com.watermelon.ui.tv.TvFolderBrowserScreen(
                        viewModel = vm,
                        onFolderClick = onFolderClick,
                        onSettingsClick = { navController.navigate(Routes.SETTINGS) }
                    )
                } else {
                    FolderBrowserScreen(
                        viewModel = vm,
                        onFolderClick = onFolderClick,
                        onSettingsClick = { navController.navigate(Routes.SETTINGS) }
                    )
                }
            }
            composable(Routes.PLAYLISTS) {
                val vm = remember { PlaylistViewModel(playlistRepository) }
                PlaylistsScreen(
                    viewModel = vm,
                    onPlaylistClick = { playlist ->
                        navController.navigate("videos/${Uri.encode(playlist.id)}?isPlaylist=true")
                    }
                )
            }
            composable(Routes.FAVORITES) {
                LaunchedEffect(Unit) {
                    navController.navigate(
                        "videos/${Uri.encode(com.watermelon.common.model.SystemPlaylist.ID_FAVOURITES)}?isPlaylist=true"
                    ) {
                        popUpTo(Routes.FAVORITES) { inclusive = true }
                    }
                }
            }
            composable(
                route = "videos/{folderPath}?isPlaylist={isPlaylist}",
                arguments = listOf(
                    navArgument("folderPath") { type = NavType.StringType },
                    navArgument("isPlaylist") { type = NavType.BoolType; defaultValue = false }
                )
            ) { backStackEntry ->
                val folderPath = Uri.decode(backStackEntry.arguments?.getString("folderPath").orEmpty())
                val isPlaylist = backStackEntry.arguments?.getBoolean("isPlaylist") ?: false
                val vm = remember(folderPath) {
                    VideoListViewModel(mediaRepository, folderPath, playlistRepository, isPlaylist)
                }
                val playlists by playlistRepository.observeAll()
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                VideoListScreen(
                    viewModel = vm,
                    onVideoClick = { item -> navController.navigate("player/${Uri.encode(item.uri)}") },
                    availablePlaylists = playlists,
                    folderName = if (isPlaylist) "Playlist" else folderPath.substringAfterLast("/"),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "player/{uri}",
                arguments = listOf(navArgument("uri") { type = NavType.StringType })
            ) { backStackEntry ->
                val mediaUri = Uri.decode(backStackEntry.arguments?.getString("uri").orEmpty())
                val controller = mediaController
                val pbController = playbackController
                if (controller == null || pbController == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Connecting…", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    val vm = remember(pbController) { PlayerViewModel(pbController) }
                    LaunchedEffect(mediaUri) { vm.onIntent(UserIntent.Play(mediaUri)) }

                    var isFavourite by remember(mediaUri) { mutableStateOf(false) }
                    LaunchedEffect(mediaUri) {
                        isFavourite = runCatching { playlistRepository.isFavourite(mediaUri) }.getOrDefault(false)
                    }

                    val vhsController = com.watermelon.ui.player.rememberVhsEffectController(
                        shaderProvider = { intensity, timeSec, w, h ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                VhsShader.build(
                                    tier = VhsCapability.detectTier(this@MainActivity),
                                    intensity = intensity, time = timeSec, width = w, height = h
                                )
                            } else null
                        },
                        reverseSound = { active, speed ->
                            if (active) { vhsReverseSound.start(speed); vhsReverseSound.setSpeed(speed) }
                            else vhsReverseSound.stop()
                        }
                    )
                    val mappedIntensity = when (settingsState.vhsIntensity) {
                        com.watermelon.ui.screens.VhsIntensity.OFF -> 0f
                        com.watermelon.ui.screens.VhsIntensity.LOW -> 0.35f
                        com.watermelon.ui.screens.VhsIntensity.MED -> 0.6f
                        com.watermelon.ui.screens.VhsIntensity.HIGH -> 1f
                    }
                    val isTelevision = remember {
                        com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(this@MainActivity)
                    }
                    val subtitleTrackState = run {
                        var track by remember(mediaUri) {
                            mutableStateOf<com.watermelon.common.model.ParsedSubtitle?>(null)
                        }
                        LaunchedEffect(mediaUri) {
                            track = discoverSubtitle(mediaUri)
                        }
                        track
                    }
                    // controller.duration is a plain Media3 Player getter, not something
                    // Compose observes — reading it directly in the composable body means it
                    // only updates when something else happens to trigger recomposition, with
                    // no guarantee that happens exactly when Media3 resolves the real duration.
                    // In practice this mostly "worked" because position ticks frequently and
                    // incidentally forces recomposition, EXCEPT right after opening a video:
                    // duration is C.TIME_UNSET (0 here) until playback reaches STATE_READY, so
                    // any seek attempted in that window — on either seek bar — silently
                    // coerced to position 0. A real Player.Listener fixes this by updating
                    // state exactly when Media3 says duration actually changed.
                    var durationMs by remember(mediaUri) { mutableStateOf(controller.duration.coerceAtLeast(0L)) }
                    DisposableEffect(controller, mediaUri) {
                        val listener = object : Player.Listener {
                            override fun onEvents(player: Player, events: Player.Events) {
                                if (events.containsAny(
                                        Player.EVENT_TIMELINE_CHANGED,
                                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                                        Player.EVENT_PLAYBACK_STATE_CHANGED
                                    )
                                ) {
                                    durationMs = player.duration.coerceAtLeast(0L)
                                }
                            }
                        }
                        controller.addListener(listener)
                        durationMs = controller.duration.coerceAtLeast(0L)
                        onDispose { controller.removeListener(listener) }
                    }

                    if (isTelevision) {
                        // TV is a separate composition (Manifest §8) — D-pad-first, no touch
                        // gestures, no VHS shader/PiP/rotation. Shares only the playback core
                        // and video surface with the phone screen.
                        com.watermelon.ui.tv.TvPlayerScreen(
                            viewModel = vm,
                            durationMs = durationMs,
                            hasPreviousTrack = remember(mediaUri) {
                                com.watermelon.ui.screens.PlaybackQueue.previousOf(mediaUri) != null
                            },
                            hasNextTrack = remember(mediaUri) {
                                com.watermelon.ui.screens.PlaybackQueue.nextOf(mediaUri) != null
                            },
                            onSkipPrevious = {
                                val prev = com.watermelon.ui.screens.PlaybackQueue.previousOf(mediaUri)
                                if (prev != null) {
                                    navController.navigate("player/${Uri.encode(prev)}") {
                                        popUpTo("player/{uri}") { inclusive = true }
                                    }
                                } else {
                                    vm.onIntent(UserIntent.Seek(0L))
                                }
                            },
                            onSkipNext = {
                                com.watermelon.ui.screens.PlaybackQueue.nextOf(mediaUri)?.let { next ->
                                    navController.navigate("player/${Uri.encode(next)}") {
                                        popUpTo("player/{uri}") { inclusive = true }
                                    }
                                }
                            },
                            subtitleTrack = subtitleTrackState,
                            surface = { modifier ->
                                AndroidView(
                                    modifier = modifier,
                                    factory = { ctx ->
                                        val view = android.view.LayoutInflater.from(ctx)
                                            .inflate(R.layout.player_view_texture, null) as PlayerView
                                        view.player = controller
                                        view.useController = false
                                        view
                                    }
                                )
                            }
                        )
                        return@composable
                    }
                    PhonePlayerScreen(
                        viewModel = vm,
                        vhs = vhsController,
                        vhsEnabled = settingsState.vhsEnabled,
                        vhsIntensity = mappedIntensity,
                        tunerSeekBarEnabled = settingsState.tunerSeekBarEnabled,
                        isInPipMode = isPiPActive,
                        onBack = { navController.popBackStack() },
                        durationMs = durationMs,
                        subtitleTrack = subtitleTrackState,
                        uri = mediaUri,
                        screenshotMode = settingsState.screenshotMode,
                        initialBrightness = savedBrightness,
                        onPipClick = {
                            com.watermelon.common.util.FileLogger.i("PiP", "onPipClick tapped")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                playbackMode = PlaybackMode.PIP
                                enterPiPMode()
                            }
                        },
                        onBackgroundClick = { enabled ->
                            playbackMode = if (enabled) PlaybackMode.BACKGROUND else PlaybackMode.NORMAL
                        },
                        onBrightnessChange = { brightness ->
                            prefs.edit().putFloat("brightness", brightness).apply()
                        },
                        onSkipToTrack = { newUri ->
                            lifecycleScope.launch {
                                runCatching { mediaRepository.markAsPlayed(newUri) }
                            }
                            navController.navigate("player/${Uri.encode(newUri)}") {
                                popUpTo("player/{uri}") { inclusive = true }
                            }
                        },
                        onLockChanged = { locked ->
                            runCatching {
                                if (locked) startLockTask() else stopLockTask()
                            }.onFailure {
                                com.watermelon.common.util.FileLogger.i("Lock", "lock task not available: ${it.message}")
                            }
                        },
                        onShare = {
                            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "video/*"
                                putExtra(android.content.Intent.EXTRA_STREAM, Uri.parse(mediaUri))
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(android.content.Intent.createChooser(sendIntent, "Share video"))
                        },
                        isFavourite = isFavourite,
                        onFavourite = { wantFavourite ->
                            isFavourite = wantFavourite
                            lifecycleScope.launch {
                                val ok = runCatching {
                                    if (wantFavourite) playlistRepository.addToFavourites(mediaUri)
                                    else playlistRepository.removeFromFavourites(mediaUri)
                                }.isSuccess
                                if (!ok) isFavourite = !wantFavourite
                            }
                        },
                        onAddToPlaylist = {
                            lifecycleScope.launch { playlistRepository.addToFavourites(mediaUri) }
                        },
                        onDelete = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val id = android.content.ContentUris.parseId(Uri.parse(mediaUri))
                                val canonical = android.content.ContentUris.withAppendedId(
                                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                                )
                                val sender = android.provider.MediaStore.createDeleteRequest(
                                    contentResolver, listOf(canonical)
                                ).intentSender
                                startIntentSenderForResult(sender, 4001, null, 0, 0, 0)
                                navController.popBackStack()
                            }
                        },
                        surface = { modifier ->
                            AndroidView(
                                modifier = modifier,
                                factory = { ctx ->
                                    val view = android.view.LayoutInflater.from(ctx)
                                        .inflate(R.layout.player_view_texture, null) as PlayerView
                                    view.player = controller
                                    view.useController = false
                                    view
                                }
                            )
                        }
                    )
                }
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    state = settingsState,
                    onStateChange = { newState ->
                        settingsState = newState
                        prefs.edit()
                            .putBoolean("vhs_enabled", newState.vhsEnabled)
                            .putString("vhs_intensity", newState.vhsIntensity.name)
                            .putBoolean("tuner_seekbar_enabled", newState.tunerSeekBarEnabled)
                            .apply()
                        if (newState.pureDark != pureDarkTheme) {
                            onPureDarkThemeChange(newState.pureDark)
                        }
                    },
                    onFolderVisibilityClick = { navController.navigate(Routes.FOLDER_VISIBILITY) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.FOLDER_VISIBILITY) {
                val vm = remember {
                    FolderViewModel(folderRepository, mediaRepository, playlistRepository, settingsStore)
                }
                val folders by vm.allFoldersForSettings.collectAsStateWithLifecycle()
                FolderVisibilityScreen(
                    folders = folders
                        .filter { !it.first.isPlaylist }
                        .map { (node, visible) -> Triple(node.path, node.displayName, visible) },
                    onToggle = { path, visible -> vm.setFolderHidden(path, !visible) },
                    onBack = { navController.popBackStack() }
                )
            }
            // NEW: Design System route
            composable(Routes.DESIGN_SYSTEM) {
                DesignSystemScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    private suspend fun discoverSubtitle(uri: String): com.watermelon.common.model.ParsedSubtitle? {
        val item = runCatching { mediaRepository.getByUri(uri) }.getOrNull() ?: return null
        return subtitleRepository.parsedFor(
            mediaItem = item,
            preferredLanguages = listOf("fa", "ar", "ur", "ku", "en")
        )
    }

    @Composable
    private fun PermissionPrompt(onRequest: () -> Unit) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
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
        const val FOLDERS = "folders"
        const val SETTINGS = "settings"
        const val FOLDER_VISIBILITY = "folder_visibility"
        const val DESIGN_SYSTEM = "design_system"  // NEW
        const val PLAYLISTS = "playlists"
        const val FAVORITES = "favorites"
    }

    private enum class PiPTier { SMALL, MID, EXPANDED }
}
