package com.example.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import com.example.data.media.ChatMediaRules
import java.io.File

/**
 * Records a voice message as AAC in an .m4a file: 32 kbps, mono, 16 kHz, at most 3 minutes.
 * [level] gives the current loudness (0..1) for the live waveform.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var startedAt = 0L
    var file: File? = null
        private set

    val isRecording: Boolean get() = recorder != null

    val elapsedMs: Long get() = if (recorder != null) SystemClock.elapsedRealtime() - startedAt else 0L

    /** Starts recording into [target]; [onMaxReached] runs when 3 minutes are reached. */
    fun start(target: File, onMaxReached: () -> Unit) {
        stopQuietly()
        target.parentFile?.mkdirs()
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(32_000)
        r.setAudioChannels(1)
        r.setAudioSamplingRate(16_000)
        r.setMaxDuration(ChatMediaRules.MAX_VOICE_MS.toInt())
        r.setOnInfoListener { _, what, _ ->
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onMaxReached()
        }
        r.setOutputFile(target.absolutePath)
        try {
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release()
            target.delete()
            throw e
        }
        recorder = r
        file = target
        startedAt = SystemClock.elapsedRealtime()
    }

    /** Loudness 0..1 since the last call (MediaRecorder's max amplitude is 0..32767). */
    fun level(): Float = runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f).coerceIn(0f, 1f)

    /** Stops and returns the file with its length, or null when it is shorter than 1 second. */
    fun stop(): Pair<File, Long>? {
        val r = recorder ?: return null
        val duration = elapsedMs.coerceAtMost(ChatMediaRules.MAX_VOICE_MS)
        val out = file
        recorder = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (!ok || out == null || duration < ChatMediaRules.MIN_VOICE_MS || !out.isFile || out.length() == 0L) {
            out?.delete()
            return null
        }
        return out to duration
    }

    /** Slide-to-cancel: stops and deletes the recording. */
    fun cancel() {
        stopQuietly()
        file?.delete()
        file = null
    }

    private fun stopQuietly() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        r.release()
    }
}
