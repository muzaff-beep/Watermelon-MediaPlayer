package com.watermelon.mediatools.job

import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import com.watermelon.common.util.FileLogger
import com.watermelon.mediatools.output.OutputFileStore
import com.watermelon.mediatools.output.OutputNaming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private const val TAG = "MediaJobManager"
private const val PROGRESS_POLL_MS = 250L

/**
 * Single owner of media-processing job state, exposed as [StateFlow] — same shape as
 * [com.watermelon.playback.controller.PlaybackControllerImpl]'s `_playbackState`, not
 * [com.watermelon.playback.service.PlaybackConnection] (that class only holds a
 * MediaController reference; it doesn't own or publish state).
 *
 * Two registration paths, since not all engines use Transformer:
 * - [register]: Transformer-backed jobs (VideoTrimmer, VideoCompressor). Caller builds a
 *   Transformer + EditedMediaItem, registers it here, then calls `transformer.start(...)`
 *   itself; progress comes from polling `Transformer.getProgress()`.
 * - [registerCoroutineJob]: non-Transformer jobs (AudioExtractor — see its class doc for
 *   why it bypasses Transformer). Caller supplies a suspend block; this class runs it on
 *   Dispatchers.IO and reports progress via callback.
 * Neither engine talks to UI directly — both push through this manager's [jobs] StateFlow.
 */
@UnstableApi
class MediaJobManager(
    private val outputFileStore: OutputFileStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val _jobs = MutableStateFlow<List<MediaJob>>(emptyList())
    val jobs: StateFlow<List<MediaJob>> = _jobs.asStateFlow()

    private val transformers = mutableMapOf<String, Transformer>()
    private val progressPollers = mutableMapOf<String, Job>()

    /**
     * Registers a new job and starts polling its progress. The caller (an engine class)
     * is responsible for building the [Transformer] with a listener that calls
     * [onCompleted]/[onError]/[onFallbackApplied] below, and for calling `transformer.start(...)`.
     */
    fun register(
        type: MediaJobType,
        inputUri: String,
        outputPath: String,
        transformer: Transformer,
    ): String {
        val id = UUID.randomUUID().toString()
        transformers[id] = transformer
        _jobs.update { it + MediaJob(id, type, inputUri, outputPath, MediaJobState.Queued) }
        setState(id, MediaJobState.Running)
        pollProgress(id, transformer)
        FileLogger.i(TAG, "job registered id=$id type=$type")
        return id
    }

    /**
     * Registers a job for engines that don't use Transformer (currently: AudioExtractor,
     * which decodes via plain MediaCodec — see its class doc for why). The caller runs its
     * own work on [scope] via [work], reporting progress through the given callback and
     * returning the finished output path on success (must throw on failure/let CancellationException propagate).
     */
    fun registerCoroutineJob(
        type: MediaJobType,
        inputUri: String,
        outputPath: String,
        work: suspend (onProgress: (Int) -> Unit) -> Unit,
    ): String {
        val id = UUID.randomUUID().toString()
        _jobs.update { it + MediaJob(id, type, inputUri, outputPath, MediaJobState.Queued) }
        setState(id, MediaJobState.Running)
        FileLogger.i(TAG, "coroutine job registered id=$id type=$type")

        progressPollers[id] = scope.launch(Dispatchers.IO) {
            try {
                work { pct ->
                    _jobs.update { list ->
                        list.map { if (it.id == id) it.copy(progressPercent = pct.coerceIn(0, 100)) else it }
                    }
                }
                completeJob(id)
            } catch (e: kotlinx.coroutines.CancellationException) {
                failCleanup(id)
                setState(id, MediaJobState.Cancelled)
                FileLogger.i(TAG, "coroutine job cancelled id=$id")
                throw e
            } catch (e: Exception) {
                failCleanup(id)
                setState(id, MediaJobState.Failed(e.message ?: "Unknown error"))
                FileLogger.e(TAG, "coroutine job failed id=$id", e)
            }
        }
        return id
    }

    private fun completeJob(id: String) {
        val job = _jobs.value.find { it.id == id } ?: run {
            FileLogger.e(TAG, "completeJob for unknown job id=$id")
            return
        }
        val displayName = File(job.outputPath).name
        val publishedUri = outputFileStore.publish(job.type, job.outputPath, displayName)
        if (publishedUri != null) {
            val awaitingDecision = job.type == MediaJobType.TRIM || job.type == MediaJobType.COMPRESS
            setState(id, MediaJobState.Completed(publishedUri.toString(), awaitingDecision))
            FileLogger.i(TAG, "job completed id=$id uri=$publishedUri awaitingDecision=$awaitingDecision")
        } else {
            setState(id, MediaJobState.Failed("Job finished but publishing the output file failed"))
            FileLogger.e(TAG, "publish failed after job completed id=$id")
        }
    }

    /**
     * Called once the user answers the "keep or delete the original?" prompt shown for a
     * completed TRIM/COMPRESS job (see [MediaJobState.Completed.awaitingOriginalFileDecision]).
     * If [deleteOriginal] is true, deletes [MediaJob.inputUri] via MediaStore.
     *
     * KNOWN GAP, not yet handled: on API 29+, [android.content.ContentResolver.delete] on a
     * MediaStore item this app didn't itself insert (which is the normal case here — these
     * are pre-existing library videos, not media-tools output) throws
     * RecoverableSecurityException rather than deleting. The real fix is
     * MediaStore.createDeleteRequest(resolver, uris) on API 30+, which returns a
     * PendingIntent the caller must launch via startIntentSenderForResult and handle the
     * result — that's an Activity-level concern this manager class can't do on its own.
     * This method's caller (the UI layer, not yet built) needs to catch
     * RecoverableSecurityException / build the delete request and re-invoke this method
     * after the user confirms via that system dialog. Not guessing at that wiring here.
     */
    fun resolveOriginalFileDecision(id: String, deleteOriginal: Boolean, contentResolver: android.content.ContentResolver) {
        val job = _jobs.value.find { it.id == id }
        val completed = job?.state as? MediaJobState.Completed
        if (completed == null || !completed.awaitingOriginalFileDecision) {
            FileLogger.e(TAG, "resolveOriginalFileDecision called for job not awaiting a decision, id=$id")
            return
        }

        if (deleteOriginal) {
            runCatching {
                contentResolver.delete(android.net.Uri.parse(job.inputUri), null, null)
            }.onFailure { e ->
                FileLogger.e(TAG, "failed to delete original for job id=$id", e)
            }.onSuccess {
                FileLogger.i(TAG, "deleted original inputUri=${job.inputUri} for job id=$id")
            }
        } else {
            FileLogger.i(TAG, "user chose to keep original for job id=$id")
        }

        setState(id, completed.copy(awaitingOriginalFileDecision = false))
    }

    private fun failCleanup(id: String) {
        _jobs.value.find { it.id == id }?.let { outputFileStore.deleteStaging(it.outputPath) }
    }

    fun onCompleted(id: String, exportResult: ExportResult) {
        stopPolling(id)
        FileLogger.i(TAG, "transformer job export done id=$id sizeBytes=${exportResult.fileSizeBytes}")
        completeJob(id)
        transformers.remove(id)
    }

    fun onError(id: String, exportException: ExportException) {
        stopPolling(id)
        failCleanup(id)
        setState(id, MediaJobState.Failed(exportException.message ?: "Unknown export error"))
        FileLogger.e(TAG, "job failed id=$id", exportException)
        transformers.remove(id)
    }

    /**
     * Transformer silently falls back to a slower re-encode path when the fast/lossless
     * path isn't achievable for the input (see blueprint §2). We only log for now —
     * surfacing this into MediaJob's state (e.g. a wasFallback flag) is a Phase 3 UI
     * decision, not something to guess at here.
     */
    fun onFallbackApplied(
        id: String,
        originalRequest: TransformationRequest,
        fallbackRequest: TransformationRequest,
    ) {
        FileLogger.w(TAG, "job id=$id fell back: $originalRequest -> $fallbackRequest")
    }

    fun cancel(id: String) {
        transformers[id]?.cancel()
        transformers.remove(id)
        progressPollers.remove(id)?.cancel() // for coroutine jobs, this cancels `work` itself
        _jobs.value.find { it.id == id }?.let { outputFileStore.deleteStaging(it.outputPath) }
        setState(id, MediaJobState.Cancelled)
        FileLogger.i(TAG, "job cancelled id=$id")
    }

    /**
     * Convenience entry point for "Extract Audio": stages an .mp3 output path via
     * [outputFileStore], then runs [AudioExtractor] as a coroutine job. This is what the
     * video-list/player overflow action (blueprint §3 UI) should call.
     */
    fun extractAudio(extractor: com.watermelon.mediatools.engine.AudioExtractor, inputPath: String, originalDisplayName: String): String {
        val outputName = OutputNaming.extractedAudioName(originalDisplayName)
        val stagingPath = outputFileStore.stagingPathFor(MediaJobType.EXTRACT_AUDIO, outputName)
        return registerCoroutineJob(MediaJobType.EXTRACT_AUDIO, inputPath, stagingPath) { onProgress ->
            extractor.extractSuspending(inputPath, stagingPath, onProgress = onProgress)
        }
    }

    private fun pollProgress(id: String, transformer: Transformer) {
        progressPollers[id] = scope.launch {
            val holder = ProgressHolder()
            while (isActive) {
                val progressState = transformer.getProgress(holder)
                if (progressState == Transformer.PROGRESS_STATE_NOT_STARTED) break
                _jobs.update { list ->
                    list.map { if (it.id == id) it.copy(progressPercent = holder.progress) else it }
                }
                delay(PROGRESS_POLL_MS)
            }
        }
    }

    private fun stopPolling(id: String) {
        progressPollers.remove(id)?.cancel()
    }

    private fun setState(id: String, state: MediaJobState) {
        _jobs.update { list -> list.map { if (it.id == id) it.copy(state = state) else it } }
    }
}
