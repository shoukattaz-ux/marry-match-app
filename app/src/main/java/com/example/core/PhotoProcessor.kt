package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
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

/** A photo ready for upload: compressed JPEG bytes without any EXIF data (no GPS, no camera info). */
class PreparedPhoto(val jpeg: ByteArray, val faceCount: Int?)

/**
 * Prepares profile photos on the phone before upload (Phase 3 photo rules):
 * upright orientation, at most 1080 px on the long side, JPEG quality 80, and an
 * on-device ML Kit face count. Re-encoding the pixels drops all EXIF metadata.
 */
object PhotoProcessor {
    const val MAX_SIDE_PX = 1080
    const val JPEG_QUALITY = 80

    suspend fun prepare(context: Context, uri: Uri): PreparedPhoto = withContext(Dispatchers.IO) {
        val bitmap = decodeUpright(context, uri)
        val faces = countFaces(bitmap)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        bitmap.recycle()
        PreparedPhoto(out.toByteArray(), faces)
    }

    /** Face count for an already stored JPEG (used when an older photo becomes the main photo). */
    suspend fun countFaces(jpeg: ByteArray): Int? = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return@withContext null
        countFaces(bitmap).also { bitmap.recycle() }
    }

    /** Number of faces, or null when the detector is unavailable (then the photo is not blocked). */
    private suspend fun countFaces(bitmap: Bitmap): Int? = suspendCancellableCoroutine { cont ->
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
        )
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { faces ->
                detector.close()
                if (cont.isActive) cont.resume(faces.size)
            }
            .addOnFailureListener {
                detector.close()
                if (cont.isActive) cont.resume(null)
            }
    }

    private fun decodeUpright(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the EXIF rotation itself.
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val scale = scaleFor(info.size.width, info.size.height)
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }

        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
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
        val scale = scaleFor(decoded.width, decoded.height)
        matrix.postScale(scale, scale)
        val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (result !== decoded) decoded.recycle()
        return result
    }

    private fun scaleFor(width: Int, height: Int): Float {
        val longSide = max(width, height)
        return if (longSide > MAX_SIDE_PX) MAX_SIDE_PX.toFloat() / longSide else 1f
    }
}
