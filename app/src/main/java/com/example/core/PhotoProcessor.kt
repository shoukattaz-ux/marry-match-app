package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.example.model.PhotoFocus
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A photo ready for upload: compressed JPEG bytes without any EXIF data (no GPS, no camera info).
 * [thumb] is a small copy for lists (match list, chat), so those screens download far less data.
 * [blurred] is a strongly blurred copy, shown to non-matches when "blur my photos" is on.
 */
class PreparedPhoto(
    val jpeg: ByteArray,
    val faceCount: Int?,
    val thumb: ByteArray? = null,
    val blurred: ByteArray? = null,
    /** Framing centred on the largest face, or null when no face was found. */
    val faceFocus: PhotoFocus? = null
)

/**
 * Prepares profile photos on the phone before upload (Phase 3 photo rules):
 * upright orientation, at most 1080 px on the long side, JPEG quality 80, and an
 * on-device ML Kit face count. Re-encoding the pixels drops all EXIF metadata.
 */
object PhotoProcessor {
    const val MAX_SIDE_PX = 1080
    const val JPEG_QUALITY = 80
    const val THUMB_SIDE_PX = 360
    const val THUMB_QUALITY = 75
    // Shrinking to a few pixels and scaling back up leaves only colours and rough shapes:
    // no face can be recognised, and the copy is only a few KB.
    const val BLUR_TINY_PX = 12
    const val BLUR_SIDE_PX = 360

    /** [withThumbnail] also makes the small list copy and the blurred copy (profile photos; selfies need neither). */
    suspend fun prepare(context: Context, uri: Uri, withThumbnail: Boolean = false): PreparedPhoto = withContext(Dispatchers.IO) {
        val bitmap = decodeUpright(context, uri)
        val faces = detectFaces(bitmap)
        val focus = faces?.maxByOrNull { it.width() * it.height() }
            ?.let { PhotoFocus.forFace(it.exactCenterX() / bitmap.width, it.exactCenterY() / bitmap.height) }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        val thumb = if (withThumbnail) thumbnail(bitmap) else null
        val blurred = if (withThumbnail) blurred(bitmap) else null
        bitmap.recycle()
        PreparedPhoto(out.toByteArray(), faces?.size, thumb, blurred, focus)
    }

    /** Blurred copy of an already stored JPEG (photos uploaded before blurring existed). */
    suspend fun blurredCopy(jpeg: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return@withContext null
        blurred(bitmap).also { bitmap.recycle() }
    }

    private fun blurred(bitmap: Bitmap): ByteArray {
        fun scaled(src: Bitmap, longSide: Int): Bitmap {
            val f = longSide.toFloat() / max(src.width, src.height)
            return Bitmap.createScaledBitmap(
                src, (src.width * f).roundToInt().coerceAtLeast(1), (src.height * f).roundToInt().coerceAtLeast(1), true
            )
        }
        // Scale up in two steps so the result is smooth instead of blocky.
        val tiny = scaled(bitmap, BLUR_TINY_PX)
        val mid = scaled(tiny, BLUR_TINY_PX * 4)
        val big = scaled(mid, BLUR_SIDE_PX)
        val out = ByteArrayOutputStream()
        big.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, out)
        listOf(tiny, mid, big).filter { it !== bitmap }.distinct().forEach { it.recycle() }
        return out.toByteArray()
    }

    private fun thumbnail(bitmap: Bitmap): ByteArray {
        val longSide = max(bitmap.width, bitmap.height)
        val scale = if (longSide > THUMB_SIDE_PX) THUMB_SIDE_PX.toFloat() / longSide else 1f
        val small = Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).roundToInt().coerceAtLeast(1), (bitmap.height * scale).roundToInt().coerceAtLeast(1), true
        )
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, out)
        if (small !== bitmap) small.recycle()
        return out.toByteArray()
    }

    /** Face count for an already stored JPEG (used when an older photo becomes the main photo). */
    suspend fun countFaces(jpeg: ByteArray): Int? = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return@withContext null
        countFaces(bitmap).also { bitmap.recycle() }
    }

    private suspend fun countFaces(bitmap: Bitmap): Int? = detectFaces(bitmap)?.size

    /** Face boxes in [bitmap] pixels, or null when the detector is unavailable (then the photo is not blocked). */
    private suspend fun detectFaces(bitmap: Bitmap): List<Rect>? = suspendCancellableCoroutine { cont ->
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
        )
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { faces ->
                detector.close()
                if (cont.isActive) cont.resume(faces.map { it.boundingBox })
            }
            .addOnFailureListener {
                detector.close()
                if (cont.isActive) cont.resume(null)
            }
    }

    /** Upright bitmap at most [maxSide] px on the long side (EXIF rotation applied). */
    internal fun decodeUpright(context: Context, uri: Uri, maxSide: Int = MAX_SIDE_PX): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the EXIF rotation itself.
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val scale = scaleFor(info.size.width, info.size.height, maxSide)
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }

        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("Unreadable image")

        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        val scale = scaleFor(decoded.width, decoded.height, maxSide)
        matrix.postScale(scale, scale)
        val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (result !== decoded) decoded.recycle()
        return result
    }

    private fun scaleFor(width: Int, height: Int, maxSide: Int): Float {
        val longSide = max(width, height)
        return if (longSide > maxSide) maxSide.toFloat() / longSide else 1f
    }
}
