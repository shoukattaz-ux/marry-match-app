package com.example.ui.chat

import android.content.Context
import android.net.Uri
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.core.media.ChatMediaPrep
import com.example.core.media.Prepared
import com.example.data.PhotoUrlSigner
import com.example.data.media.ChatMediaManager
import com.example.data.media.ChatMediaRules
import com.example.data.media.GallerySaver
import com.example.data.media.MediaFiles
import com.example.data.media.MediaSettingsStore
import com.example.data.media.MediaStatus
import com.example.data.media.MediaType
import com.example.data.media.MediaWork
import com.example.data.media.Outbox
import com.example.data.media.PendingMedia
import com.example.data.media.TransferInfo
import com.example.model.ChatMessage
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** "Preparing video..." while a video is being compressed. */
data class PreparingMedia(val type: MediaType, val progress: Int)

/**
 * Media for one open chat: sends (prepare, outbox, WorkManager), downloads, transfer states and
 * local files. Uploads and downloads themselves run in WorkManager, so they continue when the
 * screen closes or the app is killed.
 */
class ChatMediaController(
    context: Context,
    private val matchId: String,
    private val myUserId: String,
    supabase: SupabaseClient?,
    /** Called when a send finished, so the chat reloads and shows the stored message. */
    private val onSent: () -> Unit
) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val settings = MediaSettingsStore(app)
    private val thumbLinks = supabase?.let { PhotoUrlSigner.forBucket(it, ChatMediaRules.BUCKET) }

    private val _transfers = MutableStateFlow<Map<String, TransferInfo>>(emptyMap())
    /** Message id to its upload or download state. */
    val transfers: StateFlow<Map<String, TransferInfo>> = _transfers.asStateFlow()

    private val _outbox = MutableStateFlow<List<PendingMedia>>(emptyList())
    val outbox: StateFlow<List<PendingMedia>> = _outbox.asStateFlow()

    /** Changes whenever a transfer finished, so bubbles look for local files again. */
    private val _localVersion = MutableStateFlow(0)
    val localVersion: StateFlow<Int> = _localVersion.asStateFlow()

    private val _preparing = MutableStateFlow<PreparingMedia?>(null)
    val preparing: StateFlow<PreparingMedia?> = _preparing.asStateFlow()

    /** Localization key of a short notice (error or "saved"), or null. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var prepareJob: Job? = null
    private val finished = mutableSetOf<String>()

    init {
        reloadOutbox()
        scope.launch {
            WorkManager.getInstance(app).getWorkInfosByTagFlow(MediaWork.chatTag(matchId)).collect { infos ->
                val byMessage = infos.groupBy { MediaWork.messageIdOf(it.tags) }
                    .filterKeys { it != null }
                    .mapKeys { it.key!! }
                    .mapValues { (_, list) -> TransferInfo.pick(list.map(::toTransfer)) }
                    .filterValues { it != null }
                    .mapValues { it.value!! }
                _transfers.value = byMessage
                // A finished send or download: refresh local files, the outbox and the chat once.
                var sent = false
                infos.filter { it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED }.forEach { info ->
                    if (finished.add(info.id.toString())) {
                        _localVersion.update { it + 1 }
                        if (MediaWork.TAG_SEND in info.tags && info.state == WorkInfo.State.SUCCEEDED) sent = true
                    }
                }
                reloadOutbox()
                if (sent) onSent()
            }
        }
    }

    private fun toTransfer(info: WorkInfo): TransferInfo {
        val kind = if (MediaWork.TAG_SEND in info.tags) TransferInfo.Kind.SEND else TransferInfo.Kind.DOWNLOAD
        val phase = when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> TransferInfo.Phase.WAITING
            WorkInfo.State.RUNNING -> TransferInfo.Phase.RUNNING
            WorkInfo.State.FAILED -> TransferInfo.Phase.FAILED
            WorkInfo.State.SUCCEEDED -> TransferInfo.Phase.DONE
            WorkInfo.State.CANCELLED -> TransferInfo.Phase.CANCELLED
        }
        return TransferInfo(kind, phase, info.progress.getInt(MediaWork.KEY_PROGRESS, 0), info.outputData.getString(MediaWork.KEY_REASON))
    }

    private fun reloadOutbox() {
        scope.launch { _outbox.value = withContext(Dispatchers.IO) { Outbox.forMatch(app, matchId) } }
    }

    fun clearNotice() { _notice.value = null }

    fun showNotice(key: String) { _notice.value = key }

    fun localFile(messageId: String, type: MediaType): File = MediaFiles.localFile(app, matchId, messageId, type)

    fun hasLocalCopy(message: ChatMessage, type: MediaType): Boolean =
        MediaFiles.hasLocalCopy(app, matchId, message.id, type, if (message.senderId == myUserId) null else message.media?.bytes)

    /** Signed link for the small preview of a file that is still on the server. */
    suspend fun thumbUrl(path: String): String? = thumbLinks?.url(path)

    fun outboxFile(pending: PendingMedia): File = Outbox.file(app, pending)
    fun outboxThumb(pending: PendingMedia): File? = Outbox.thumb(app, pending)

    // ---------- Sending ----------

    private fun hasRoom(): Boolean {
        val ok = ChatMediaRules.hasRoomFor(ChatMediaRules.MAX_BYTES, app.filesDir.usableSpace)
        if (!ok) _notice.value = "media_no_space"
        return ok
    }

    fun sendImage(uri: Uri, caption: String?) {
        if (!hasRoom()) return
        val messageId = UUID.randomUUID().toString()
        prepareJob = scope.launch {
            _preparing.value = PreparingMedia(MediaType.IMAGE, 0)
            val result = ChatMediaPrep.prepareImage(app, uri, MediaFiles.outboxDir(app, messageId))
            _preparing.value = null
            enqueue(messageId, MediaType.IMAGE, result, caption)
        }
    }

    fun sendVideo(uri: Uri, caption: String?) {
        if (!hasRoom()) return
        val messageId = UUID.randomUUID().toString()
        prepareJob = scope.launch {
            _preparing.value = PreparingMedia(MediaType.VIDEO, 0)
            val result = ChatMediaPrep.prepareVideo(app, uri, MediaFiles.outboxDir(app, messageId)) { p ->
                _preparing.value = PreparingMedia(MediaType.VIDEO, p)
            }
            _preparing.value = null
            enqueue(messageId, MediaType.VIDEO, result, caption)
        }
    }

    /** "Preparing video..." cancelled. */
    fun cancelPreparing() {
        prepareJob?.cancel()
        _preparing.value = null
    }

    /** A new id and the outbox file a voice message is recorded into. */
    fun newVoiceTarget(): Pair<String, File> {
        val id = UUID.randomUUID().toString()
        return id to File(MediaFiles.outboxDir(app, id), MediaType.VOICE.fileName)
    }

    fun sendVoice(messageId: String, file: File, durationMs: Long) {
        enqueue(messageId, MediaType.VOICE, Prepared.Ready(file, null, durationMs), null)
    }

    fun discardVoice(messageId: String) {
        Outbox.remove(app, messageId)
    }

    private fun enqueue(messageId: String, type: MediaType, result: Prepared, caption: String?) {
        when (result) {
            is Prepared.Refused -> {
                Outbox.remove(app, messageId)
                _notice.value = result.reasonKey
            }
            is Prepared.Ready -> {
                if (!ChatMediaRules.mimeAllowed(type.mime) || result.file.length() > ChatMediaRules.MAX_BYTES) {
                    Outbox.remove(app, messageId)
                    _notice.value = if (result.file.length() > ChatMediaRules.MAX_BYTES) "media_too_large" else "media_type_refused"
                    return
                }
                // The worker expects the standard name (image.jpg, voice.m4a, video.mp4).
                val file = result.file
                ChatMediaManager.send(
                    app,
                    PendingMedia(
                        messageId = messageId,
                        matchId = matchId,
                        senderId = myUserId,
                        type = type.wire,
                        fileName = file.name,
                        thumbName = result.thumb?.name,
                        caption = caption?.trim()?.take(ChatMediaRules.MAX_CAPTION)?.ifBlank { null },
                        durationMs = result.durationMs,
                        bytes = file.length(),
                        createdAtMs = System.currentTimeMillis()
                    )
                )
                reloadOutbox()
            }
        }
    }

    fun retrySend(messageId: String) {
        ChatMediaManager.retrySend(app, messageId)
        reloadOutbox()
    }

    fun cancelSend(messageId: String) {
        scope.launch {
            ChatMediaManager.cancelSend(app, messageId)
            reloadOutbox()
        }
    }

    // ---------- Receiving ----------

    fun download(message: ChatMessage, type: MediaType) {
        val media = message.media ?: return
        if (media.status != MediaStatus.UPLOADED) return
        ChatMediaManager.download(app, message.id, matchId, type, media.path, media.bytes)
    }

    fun cancelDownload(messageId: String) = ChatMediaManager.cancelDownload(app, messageId)

    /** New received files: download by themselves if Settings > Chat media allows it. */
    fun autoDownload(messages: List<ChatMessage>) {
        val candidates = messages.filter {
            it.senderId != myUserId && it.media?.status == MediaStatus.UPLOADED && it.id !in _transfers.value
        }
        if (candidates.isEmpty()) return
        scope.launch {
            val current = settings.current()
            candidates.forEach { message ->
                val type = MediaType.fromWire(message.type) ?: return@forEach
                val media = message.media ?: return@forEach
                ChatMediaManager.autoDownload(app, message.id, matchId, type, media.path, media.bytes, current.forType(type))
            }
        }
    }

    // ---------- Other ----------

    fun saveToGallery(file: File, type: MediaType) {
        scope.launch {
            _notice.value = if (GallerySaver.save(app, file, type)) "media_saved" else "media_save_failed"
        }
    }

    /** Blocked: stop every upload and download of this chat. */
    fun stopChat() {
        scope.launch {
            ChatMediaManager.stopChat(app, matchId)
            reloadOutbox()
        }
    }

    fun close() = scope.cancel()
}
