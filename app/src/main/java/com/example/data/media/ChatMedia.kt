package com.example.data.media

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** The three media kinds; [wire] is messages.type, [fileName] the file inside the message folder. */
enum class MediaType(val wire: String, val fileName: String, val mime: String, val extension: String) {
    IMAGE("image", "image.jpg", "image/jpeg", "jpg"),
    VOICE("voice", "voice.m4a", "audio/mp4", "m4a"),
    VIDEO("video", "video.mp4", "video/mp4", "mp4");

    companion object {
        fun fromWire(type: String?): MediaType? = entries.firstOrNull { it.wire == type }
    }
}

/** messages.media_status */
object MediaStatus {
    const val UPLOADED = "uploaded"
    const val DOWNLOADED = "downloaded"
    const val EXPIRED = "expired"
}

object ChatMediaRules {
    const val BUCKET = "chat-media"
    const val THUMB_FILE = "thumb.jpg"
    const val THUMB_MIME = "image/jpeg"
    /** Same limit as the bucket on the server. */
    const val MAX_BYTES = 20L * 1024 * 1024
    const val MAX_CAPTION = 300
    const val MAX_VOICE_MS = 3 * 60 * 1000L
    const val MIN_VOICE_MS = 1000L
    const val MAX_VIDEO_MS = 30_000L
    /** Never retry an upload or download more than this many times automatically. */
    const val MAX_AUTO_RETRIES = 3
    /** Keep this much space free on the phone besides the file itself. */
    const val STORAGE_MARGIN_BYTES = 20L * 1024 * 1024

    private val allowedMimes = setOf("image/jpeg", "audio/mp4", "video/mp4")

    fun mimeAllowed(mime: String) = mime in allowedMimes

    /** Path in the bucket: <match_id>/<message_id>/<file>. */
    fun remotePath(matchId: String, messageId: String, file: String) = "$matchId/$messageId/$file"

    /** Can a file of [bytes] be written, given [freeBytes] on the phone? */
    fun hasRoomFor(bytes: Long, freeBytes: Long) = freeBytes - bytes >= STORAGE_MARGIN_BYTES
}

/**
 * Where files live on the phone. Received and sent media are kept in the app's private storage
 * (filesDir/chat_media/<match_id>/), so they work offline; files waiting to be sent are in the
 * outbox (filesDir/chat_media_outbox/<message_id>/) until the upload has finished.
 */
object MediaFiles {
    fun localFile(context: Context, matchId: String, messageId: String, type: MediaType): File =
        File(File(context.filesDir, "chat_media/$matchId"), "$messageId.${type.extension}")

    /** A local copy that is complete: [expectedBytes] is media_bytes from the server (null = unknown). */
    fun hasLocalCopy(context: Context, matchId: String, messageId: String, type: MediaType, expectedBytes: Long?): Boolean {
        val file = localFile(context, matchId, messageId, type)
        return file.isFile && file.length() > 0 && (expectedBytes == null || file.length() == expectedBytes)
    }

    fun outboxDir(context: Context, messageId: String) = File(context.filesDir, "chat_media_outbox/$messageId")

    fun outboxRoot(context: Context) = File(context.filesDir, "chat_media_outbox")

    /** Removes everything stored for a chat, e.g. after unmatching. */
    fun deleteChat(context: Context, matchId: String) {
        File(context.filesDir, "chat_media/$matchId").deleteRecursively()
    }
}

/** A media message waiting to be sent; stored as outbox/<message_id>/meta.json so it survives restarts. */
@Serializable
data class PendingMedia(
    val messageId: String,
    val matchId: String,
    val senderId: String,
    val type: String,
    val fileName: String,
    val thumbName: String? = null,
    val caption: String? = null,
    val durationMs: Long? = null,
    val bytes: Long,
    val createdAtMs: Long,
    /** Set when automatic attempts have stopped; the bubble then offers "tap to retry". */
    val failed: Boolean = false,
    /** Localization key explaining a final failure, e.g. "media_too_large". */
    val failureKey: String? = null
) {
    val mediaType: MediaType get() = MediaType.fromWire(type) ?: MediaType.IMAGE
}

object Outbox {
    private val json = Json { ignoreUnknownKeys = true }

    fun write(context: Context, pending: PendingMedia) {
        val dir = MediaFiles.outboxDir(context, pending.messageId)
        dir.mkdirs()
        File(dir, "meta.json").writeText(json.encodeToString(PendingMedia.serializer(), pending))
    }

    fun read(context: Context, messageId: String): PendingMedia? =
        runCatching {
            val file = File(MediaFiles.outboxDir(context, messageId), "meta.json")
            if (file.isFile) json.decodeFromString(PendingMedia.serializer(), file.readText()) else null
        }.getOrNull()

    fun file(context: Context, pending: PendingMedia) = File(MediaFiles.outboxDir(context, pending.messageId), pending.fileName)

    fun thumb(context: Context, pending: PendingMedia) =
        pending.thumbName?.let { File(MediaFiles.outboxDir(context, pending.messageId), it) }

    /** Everything waiting to be sent in one chat, oldest first. */
    fun forMatch(context: Context, matchId: String): List<PendingMedia> =
        MediaFiles.outboxRoot(context).listFiles().orEmpty()
            .mapNotNull { read(context, it.name) }
            .filter { it.matchId == matchId }
            .sortedBy { it.createdAtMs }

    fun remove(context: Context, messageId: String) {
        MediaFiles.outboxDir(context, messageId).deleteRecursively()
    }
}
