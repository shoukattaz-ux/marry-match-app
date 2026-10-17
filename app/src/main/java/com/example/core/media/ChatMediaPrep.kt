package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.core.PhotoProcessor
import com.example.data.media.ChatMediaRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

/** A file ready for the outbox, or the localization key of why it cannot be sent. */
sealed interface Prepared {
    data class Ready(val file: File, val thumb: File?, val durationMs: Long?) : Prepared
    data class Refused(val reasonKey: String) : Prepared
}

/**
 * Prepares chat media on the phone before upload. Photos: upright, longest side 1280 px,
 * JPEG 75 (lower if needed to stay under 500 KB), no EXIF data at all (re-encoding drops it,
 * including GPS), plus a 24 px preview. Videos: Media3 Transformer to 720p H.264 at about
 * 1.2 Mbps with AAC audio, plus a 320 px first-frame preview.
 */
object ChatMediaPrep {
    const val IMAGE_SIDE_PX = 1280
    const val IMAGE_TARGET_BYTES = 500 * 1024
    const val THUMB_SIDE_PX = 24
    const val VIDEO_HEIGHT = 720
    const val VIDEO_BITRATE = 1_200_000
    const val VIDEO_THUMB_WIDTH = 320

    suspend fun prepareImage(context: Context, uri: Uri, outDir: File): Prepared = withContext(Dispatchers.IO) {
        try {
            outDir.mkdirs()
            val bitmap = PhotoProcessor.decodeUpright(context, uri, IMAGE_SIDE_PX)
            val image = File(outDir, "image.jpg")
            var quality = 75
            do {
                image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                quality -= 10
            } while (image.length() > IMAGE_TARGET_BYTES && quality >= 45)
            val thumb = File(outDir, "thumb.jpg")
            writeScaled(bitmap, thumb, THUMB_SIDE_PX, longSide = true, quality = 50)
            bitmap.recycle()
            if (image.length() > ChatMediaRules.MAX_BYTES) Prepared.Refused("media_too_large")
            else Prepared.Ready(image, thumb, null)
        } catch (e: OutOfMemoryError) {
            Prepared.Refused("media_prepare_failed")
        } catch (e: Exception) {
            Prepared.Refused(if (isNoSpace(e)) "media_no_space" else "media_prepare_failed")
        }
    }

    /** Length of a picked or recorded video, or null when it cannot be read. */
    suspend fun videoDurationMs(context: Context, uri: Uri): Long? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Compresses a video; [onProgress] gets 0..100 while "Preparing video..." is shown. */
    @OptIn(UnstableApi::class)
    suspend fun prepareVideo(context: Context, uri: Uri, outDir: File, onProgress: (Int) -> Unit): Prepared {
        val duration = videoDurationMs(context, uri) ?: return Prepared.Refused("media_prepare_failed")
        if (duration > ChatMediaRules.MAX_VIDEO_MS + 500) return Prepared.Refused("media_video_too_long")
        outDir.mkdirs()
        val output = File(outDir, "video.mp4")
        output.delete()

        val result = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Result<ExportResult>> { cont ->
                val handler = Handler(Looper.getMainLooper())
                val holder = ProgressHolder()
                val encoder = DefaultEncoderFactory.Builder(context.applicationContext)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(VIDEO_BITRATE).build())
                    .build()
                lateinit var poll: Runnable
                val transformer = Transformer.Builder(context.applicationContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoder)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            handler.removeCallbacks(poll)
                            if (cont.isActive) cont.resume(Result.success(exportResult))
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            handler.removeCallbacks(poll)
                            if (cont.isActive) cont.resume(Result.failure(exportException))
                        }
                    })
                    .build()
                val item = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                    .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(VIDEO_HEIGHT))))
                    .build()
                poll = Runnable {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                    handler.postDelayed(poll, 250)
                }
                cont.invokeOnCancellation {
                    handler.post {
                        handler.removeCallbacks(poll)
                        transformer.cancel()
                    }
                }
                try {
                    transformer.start(item, output.absolutePath)
                    handler.post(poll)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(Result.failure(e))
                }
            }
        }

        if (result.isFailure || !output.isFile) {
            output.delete()
            return Prepared.Refused(if (result.exceptionOrNull()?.let(::isNoSpace) == true) "media_no_space" else "media_prepare_failed")
        }
        if (output.length() > ChatMediaRules.MAX_BYTES) {
            output.delete()
            return Prepared.Refused("media_video_too_large")
        }
        val thumb = withContext(Dispatchers.IO) { videoThumb(output, File(outDir, "thumb.jpg")) }
        val exported = result.getOrNull()?.durationMs?.takeIf { it > 0 } ?: duration
        return Prepared.Ready(output, thumb, exported)
    }

    /** First frame, 320 px wide, JPEG 60. */
    private fun videoThumb(video: File, target: File): File? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(video.absolutePath)
            val frame = retriever.getFrameAtTime(0) ?: return null
            writeScaled(frame, target, VIDEO_THUMB_WIDTH, longSide = false, quality = 60)
            frame.recycle()
            target
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun writeScaled(bitmap: Bitmap, target: File, size: Int, longSide: Boolean, quality: Int) {
        val scale = if (longSide) size.toFloat() / max(bitmap.width, bitmap.height) else size.toFloat() / bitmap.width
        val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        target.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        if (small !== bitmap) small.recycle()
    }

    private fun isNoSpace(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any { it.message?.contains("ENOSPC") == true || it.message?.contains("No space") == true }
}
