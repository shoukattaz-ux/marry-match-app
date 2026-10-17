package com.example.data.media

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Save to phone": copies a downloaded photo or video into the public gallery
 * (Pictures/MarryMatch, Movies/MarryMatch). Android 10 and newer need no permission for this;
 * older phones need WRITE_EXTERNAL_STORAGE, which the chat screen asks for first.
 */
object GallerySaver {

    /** True when saving needs the storage permission (Android 9 and older). */
    val needsPermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    suspend fun save(context: Context, file: File, type: MediaType): Boolean = withContext(Dispatchers.IO) {
        if (type == MediaType.VOICE || !file.isFile) return@withContext false
        val isVideo = type == MediaType.VIDEO
        val name = "MarryMatch_${System.currentTimeMillis()}.${type.extension}"
        val resolver = context.contentResolver
        val collection = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            isVideo -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, type.mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val dir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/MarryMatch")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val base = Environment.getExternalStoragePublicDirectory(if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES)
                val dir = File(base, "MarryMatch").apply { mkdirs() }
                @Suppress("DEPRECATION")
                put(MediaStore.MediaColumns.DATA, File(dir, name).absolutePath)
            }
        }
        val uri = resolver.insert(collection, values) ?: return@withContext false
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw java.io.IOException("no stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
