package com.watermelon.mediatools.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.watermelon.common.util.FileLogger
import com.watermelon.mediatools.job.MediaJob
import com.watermelon.mediatools.job.MediaJobManager
import com.watermelon.mediatools.job.MediaJobState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val TAG = "MediaJobService"
private const val CHANNEL_ID = "media_tools_jobs"
private const val NOTIFICATION_ID = 4200 // arbitrary, app-unique; distinct from playback's own id space
private const val ACTION_CANCEL_JOB = "com.watermelon.mediatools.action.CANCEL_JOB"
private const val EXTRA_JOB_ID = "job_id"

/**
 * Foreground service mirroring WatermelonPlaybackService's role for playback: started when a
 * job begins, holds a percentage + cancel notification, stops itself when the job queue is
 * empty. Uses the "dataSync" foreground service type (confirmed via Android docs this
 * session) rather than "mediaPlayback", since this isn't audio/video playback.
 *
 * IMPORTANT, not yet resolved: this class needs a real [MediaJobManager] instance to observe
 * ([jobs] StateFlow) and to route cancel actions into. Since MediaJobManager isn't currently
 * a singleton/DI-provided object anywhere in this codebase (no DI framework exists in this
 * repo, confirmed by audit), [jobManagerProvider] is a placeholder static var the app must
 * set once at startup (e.g. in Application.onCreate or MainActivity) before this service can
 * do anything real. This is the same shape problem WatermelonPlaybackService likely solves
 * differently (MediaController connects to it, not the other way around) -- worth revisiting
 * once real app-level wiring exists, not guessing at a DI setup here.
 *
 * On Android 15+ (VANILLA_ICE_CREAM), dataSync foreground services have a 6-hour time limit
 * enforced by the system (onTimeout() callback) -- confirmed via docs. Not handled here;
 * long-running compress jobs on very large files could theoretically hit this, worth
 * revisiting if that becomes a real issue.
 *
 * NOT run on-device.
 */
class MediaJobService : Service() {

    companion object {
        /** Must be set once by the app before this service is started. See class doc. */
        var jobManagerProvider: (() -> MediaJobManager)? = null

        fun cancelIntent(context: Context, jobId: String): PendingIntent {
            val intent = Intent(context, MediaJobService::class.java).apply {
                action = ACTION_CANCEL_JOB
                putExtra(EXTRA_JOB_ID, jobId)
            }
            return PendingIntent.getService(
                context, jobId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob())
    private var observerJob: Job? = null
    private var jobManager: MediaJobManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = jobManager ?: jobManagerProvider?.invoke()?.also { jobManager = it }
        if (manager == null) {
            FileLogger.e(TAG, "onStartCommand but jobManagerProvider isn't set -- stopping self")
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_CANCEL_JOB) {
            intent.getStringExtra(EXTRA_JOB_ID)?.let { manager.cancel(it) }
            return START_NOT_STICKY
        }

        if (observerJob == null) {
            observerJob = manager.jobs
                .onEach { jobs -> onJobsChanged(jobs) }
                .launchIn(serviceScope)
        }

        return START_NOT_STICKY
    }

    private fun onJobsChanged(jobs: List<MediaJob>) {
        val active = jobs.filter { it.state is MediaJobState.Queued || it.state is MediaJobState.Running }
        if (active.isEmpty()) {
            FileLogger.i(TAG, "no active jobs -- stopping foreground")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        // Single notification summarizing the first active job; a multi-job notification
        // layout (one line per job) is a reasonable v2 improvement, not built here.
        startForeground(NOTIFICATION_ID, buildNotification(active.first()))
    }

    private fun buildNotification(job: MediaJob): Notification {
        val title = when (job.type) {
            com.watermelon.mediatools.job.MediaJobType.EXTRACT_AUDIO -> "Extracting audio..."
            com.watermelon.mediatools.job.MediaJobType.TRIM -> "Trimming video..."
            com.watermelon.mediatools.job.MediaJobType.COMPRESS -> "Compressing video..."
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("${job.progressPercent}%")
            .setProgress(100, job.progressPercent, false)
            .setSmallIcon(android.R.drawable.stat_sys_download) // placeholder -- app should supply a real icon
            .setOngoing(true)
            .addAction(0, "Cancel", cancelIntent(this, job.id))
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Media processing", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        observerJob?.cancel()
        serviceScope.launch { /* no-op, keeps SupervisorJob API shape consistent if extended later */ }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
