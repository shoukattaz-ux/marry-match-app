package com.example.data.media

import com.example.core.AppConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.hours

/** The server refused for good (no point retrying), e.g. not allowed or too large. */
class MediaRefusedException(val code: Int) : IOException("refused: $code")

/** The file is not on the server (expired, or removed after download). */
class MediaNotFoundException : IOException("not found")

/** Raised from progress callbacks when the user cancelled. */
class MediaCancelledException : IOException("cancelled")

class NotSignedInException : IOException("not signed in")

@Serializable
data class IncomingMedia(
    val id: String,
    @SerialName("match_id") val matchId: String,
    val type: String,
    @SerialName("media_path") val mediaPath: String,
    @SerialName("thumb_path") val thumbPath: String? = null,
    @SerialName("media_bytes") val mediaBytes: Long
)

@Serializable
private data class DownloadedDto(
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("thumb_path") val thumbPath: String? = null,
    @SerialName("can_remove") val canRemove: Boolean = false
)

/**
 * Talks to the chat-media bucket and the media functions (migration 023). Uploads and downloads
 * go through OkHttp directly so they report progress and can be cancelled. Nothing here logs
 * paths or contents.
 */
class ChatMediaRemote(private val client: SupabaseClient) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Waits for the saved session (a worker may run before the app has loaded it). */
    private suspend fun accessToken(): String {
        client.auth.awaitInitialization()
        return client.auth.currentAccessTokenOrNull() ?: throw NotSignedInException()
    }

    suspend fun myUserId(): String {
        client.auth.awaitInitialization()
        return client.auth.currentUserOrNull()?.id ?: throw NotSignedInException()
    }

    /** Uploads [file] to chat-media/[path]. Re-uploading (retry) replaces a partial earlier file. */
    suspend fun upload(
        path: String,
        file: File,
        mime: String,
        onProgress: (sent: Long, total: Long) -> Unit,
        isCancelled: () -> Boolean
    ) {
        if (!ChatMediaRules.mimeAllowed(mime)) throw MediaRefusedException(415)
        if (file.length() > ChatMediaRules.MAX_BYTES) throw MediaRefusedException(413)
        val token = accessToken()
        val body = ProgressFileBody(file, mime, onProgress, isCancelled)
        val request = Request.Builder()
            .url("${AppConfig.SUPABASE_URL}/storage/v1/object/${ChatMediaRules.BUCKET}/$path")
            .addHeader("apikey", AppConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("x-upsert", "true")
            .post(body)
            .build()
        withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw httpError(response.code)
            }
        }
    }

    /** Inserts the message after the upload; the id comes from the phone, so a retry never duplicates it. */
    suspend fun insertMessage(pending: PendingMedia, mediaPath: String, thumbPath: String?) {
        try {
            client.from("messages").insert(buildJsonObject {
                put("id", pending.messageId)
                put("match_id", pending.matchId)
                put("sender_id", pending.senderId)
                put("type", pending.type)
                put("media_path", mediaPath)
                put("thumb_path", thumbPath?.let { JsonPrimitive(it) } ?: JsonNull)
                put("media_bytes", pending.bytes)
                put("duration_ms", pending.durationMs?.let { JsonPrimitive(it) } ?: JsonNull)
                put("media_status", MediaStatus.UPLOADED)
                put("body", pending.caption?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
            })
        } catch (e: Throwable) {
            // Already inserted by an earlier attempt that lost its answer: that is success.
            val text = e.message.orEmpty()
            if (text.contains("duplicate key") || text.contains("23505")) return
            throw e
        }
    }

    /** A link valid for one hour, or null when the file is no longer on the server. */
    suspend fun signedUrl(path: String): String? {
        accessToken()
        return try {
            client.storage.from(ChatMediaRules.BUCKET)
                .createSignedUrls(1.hours, listOf(path))
                .firstOrNull { it.error == null && it.path == path }
                ?.signedURL
        } catch (e: SerializationException) {
            null  // a missing file comes back without a link
        }
    }

    /** Downloads [url] into [target]; throws [MediaNotFoundException] when the file is gone. */
    suspend fun download(
        url: String,
        target: File,
        onProgress: (received: Long, total: Long) -> Unit,
        isCancelled: () -> Boolean
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (response.code == 400 || response.code == 404) throw MediaNotFoundException()
            if (!response.isSuccessful) throw httpError(response.code)
            val body = response.body ?: throw IOException("empty body")
            val total = body.contentLength()
            target.parentFile?.mkdirs()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var received = 0L
                    while (true) {
                        if (isCancelled()) throw MediaCancelledException()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        onProgress(received, total)
                    }
                }
            }
        }
    }

    /** Marks a received file as downloaded; returns the paths to remove, or empty while it is reported. */
    suspend fun markDownloaded(messageId: String): List<String> {
        val dto = client.postgrest.rpc("mark_media_downloaded", buildJsonObject { put("p_message_id", messageId) })
            .decodeAs<DownloadedDto>()
        return if (dto.canRemove) listOfNotNull(dto.mediaPath, dto.thumbPath) else emptyList()
    }

    /** Asks the server to confirm a missing file; returns the message's media_status. */
    suspend fun markMissing(messageId: String): String =
        client.postgrest.rpc("mark_media_missing", buildJsonObject { put("p_message_id", messageId) })
            .decodeAs<String>()

    /** Removes files; refused (e.g. reported message) or failed removals are ignored. */
    suspend fun removeQuietly(paths: List<String>) {
        if (paths.isEmpty()) return
        runCatching { client.storage.from(ChatMediaRules.BUCKET).delete(paths) }
    }

    /** Received files not downloaded yet, in one chat or in all chats. */
    suspend fun incomingNotDownloaded(matchId: String?): List<IncomingMedia> {
        val me = myUserId()
        return client.from("messages")
            .select(columns = Columns.list("id", "match_id", "type", "media_path", "thumb_path", "media_bytes")) {
                filter {
                    eq("media_status", MediaStatus.UPLOADED)
                    neq("sender_id", me)
                    if (matchId != null) eq("match_id", matchId)
                }
                limit(50L)
            }
            .decodeList<IncomingMedia>()
    }

    private fun httpError(code: Int): IOException =
        if (code in 400..499 && code != 408 && code != 429) MediaRefusedException(code) else IOException("http $code")
}

/** Streams a file and reports how much was sent; stops when the user cancels. */
private class ProgressFileBody(
    private val file: File,
    mime: String,
    private val onProgress: (Long, Long) -> Unit,
    private val isCancelled: () -> Boolean
) : RequestBody() {
    private val type = mime.toMediaType()
    override fun contentType() = type
    override fun contentLength() = file.length()
    override fun writeTo(sink: BufferedSink) {
        val total = file.length()
        file.source().buffer().use { source ->
            var sent = 0L
            while (true) {
                if (isCancelled()) throw MediaCancelledException()
                val read = source.read(sink.buffer, 64 * 1024L)
                if (read < 0) break
                sink.emit()
                sent += read
                onProgress(sent, total)
            }
        }
    }
}
