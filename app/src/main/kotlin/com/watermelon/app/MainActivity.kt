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
    }

    private enum class PiPTier { SMALL, MID, EXPANDED }
}