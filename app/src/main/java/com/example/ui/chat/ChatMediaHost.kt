package com.example.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.core.media.VoiceRecorder
import com.example.data.MarryMatchRepository
import com.example.data.media.GallerySaver
import com.example.data.media.MediaType
import com.example.data.media.MediaUiLogic
import com.example.data.media.MediaUiState
import com.example.data.media.PendingMedia
import com.example.data.media.TransferInfo
import com.example.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Everything media in one chat screen: the controller, voice player and recorder, pickers,
 * camera and permission requests, and the dialogs. The chat screen calls into this for the
 * message list, the input bar and the banners.
 */
class ChatMediaUi internal constructor(
    private val context: Context,
    val controller: ChatMediaController,
    val voicePlayer: VoicePlayer,
    internal val recorder: VoiceRecorder,
    private val scope: CoroutineScope,
    private val myUserId: String
) {
    /** Blocked: no uploads, downloads or media actions in this chat. */
    var blocked by mutableStateOf(false)
    var recording by mutableStateOf<VoiceRecording?>(null)
        private set
    internal var captionFor by mutableStateOf<Pair<Uri, Boolean>?>(null)
    internal var askMic by mutableStateOf(false)
    internal var viewerStart by mutableStateOf<String?>(null)
    internal var fullscreenVideo by mutableStateOf<File?>(null)
    internal var pendingSave: Pair<File, MediaType>? = null

    internal lateinit var pickPhoto: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>
    internal lateinit var pickVideo: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>
    internal lateinit var cameraPermission: ManagedActivityResultLauncher<String, Boolean>
    internal lateinit var micPermission: ManagedActivityResultLauncher<String, Boolean>
    internal lateinit var storagePermission: ManagedActivityResultLauncher<String, Boolean>
    internal lateinit var takePhoto: ManagedActivityResultLauncher<Uri, Boolean>
    internal lateinit var recordVideo: ManagedActivityResultLauncher<Uri, Boolean>
    internal var captureUri by mutableStateOf<Uri?>(null)
    internal var pendingCamera: AttachChoice? = null

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun onAttach(choice: AttachChoice) {
        if (blocked) return
        when (choice) {
            AttachChoice.PHOTO_GALLERY -> pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            AttachChoice.VIDEO_GALLERY -> pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            AttachChoice.PHOTO_CAMERA, AttachChoice.VIDEO_CAMERA -> {
                // The app declares CAMERA (selfie check), so the camera app may only be opened once it is granted.
                if (granted(Manifest.permission.CAMERA)) launchCamera(choice)
                else {
                    pendingCamera = choice
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            }
        }
    }

    internal fun launchCamera(choice: AttachChoice) {
        val video = choice == AttachChoice.VIDEO_CAMERA
        val uri = newCaptureUri(context, if (video) "mp4" else "jpg")
        captureUri = uri
        if (video) recordVideo.launch(uri) else takePhoto.launch(uri)
    }

    fun sendPicked(uri: Uri, isVideo: Boolean, caption: String) {
        captionFor = null
        if (blocked) return
        if (isVideo) controller.sendVideo(uri, caption) else controller.sendImage(uri, caption)
    }

    // ---------- Voice ----------

    /** Hold on the mic: starts recording, or asks for the microphone first. */
    fun startRecording(): Boolean {
        if (blocked || recording != null) return false
        if (!granted(Manifest.permission.RECORD_AUDIO)) {
            askMic = true
            return false
        }
        val (id, file) = controller.newVoiceTarget()
        return try {
            voicePlayer.pause()
            recorder.start(file) { scope.launch { finishRecording(send = true) } }
            recording = VoiceRecording(id, file)
            true
        } catch (e: Exception) {
            controller.discardVoice(id)
            controller.showNotice("media_record_failed")
            false
        }
    }

    fun finishRecording(send: Boolean) {
        val rec = recording ?: return
        recording = null
        if (!send) {
            recorder.cancel()
            controller.discardVoice(rec.messageId)
            return
        }
        val result = recorder.stop()
        if (result == null) {
            controller.discardVoice(rec.messageId)
            controller.showNotice("media_voice_too_short")
        } else {
            controller.sendVoice(rec.messageId, result.first, result.second)
        }
    }

    // ---------- Message list ----------

    /** Outbox items not yet in the chat (the server row has not arrived). */
    fun pendingFor(messages: List<ChatMessage>, outbox: List<PendingMedia>): List<PendingMedia> {
        val ids = messages.mapTo(HashSet()) { it.id }
        return outbox.filter { it.messageId !in ids }
    }

    fun save(file: File, type: MediaType) {
        if (GallerySaver.needsPermission && !granted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            pendingSave = file to type
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            controller.saveToGallery(file, type)
        }
    }

    /** Downloaded voice messages in chat order, for "play the next one automatically". */
    fun voiceQueue(messages: List<ChatMessage>): List<Pair<String, File>> =
        messages.filter { it.type == MediaType.VOICE.wire && it.media != null && controller.hasLocalCopy(it, MediaType.VOICE) }
            .map { it.id to controller.localFile(it.id, MediaType.VOICE) }

    fun isMine(message: ChatMessage) = message.senderId == myUserId
}

@Composable
fun rememberChatMediaUi(matchId: String, repository: MarryMatchRepository, onSent: () -> Unit): ChatMediaUi {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val myId = repository.currentUser.value.id
    val ui = remember(matchId) {
        ChatMediaUi(
            context = context,
            controller = ChatMediaController(context, matchId, myId, repository.supabase, onSent),
            voicePlayer = VoicePlayer(context),
            recorder = VoiceRecorder(context),
            scope = scope,
            myUserId = myId
        )
    }
    var captureUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    ui.pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ui.captionFor = uri to false
    }
    ui.pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ui.captionFor = uri to true
    }
    ui.takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = ui.captureUri ?: captureUri
        if (ok && uri != null) ui.captionFor = uri to false
    }
    ui.recordVideo = rememberLauncherForActivityResult(CaptureShortVideo()) { ok ->
        val uri = ui.captureUri ?: captureUri
        if (ok && uri != null) ui.captionFor = uri to true
    }
    ui.cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val choice = ui.pendingCamera
        ui.pendingCamera = null
        if (granted && choice != null) ui.launchCamera(choice) else if (!granted) ui.controller.showNotice("media_camera_denied")
    }
    ui.micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ui.controller.showNotice(if (granted) "media_mic_ready" else "media_mic_denied")
    }
    ui.storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val save = ui.pendingSave
        ui.pendingSave = null
        if (granted && save != null) ui.controller.saveToGallery(save.first, save.second)
        else if (!granted) ui.controller.showNotice("media_save_failed")
    }
    // The capture uri must survive the camera app (the activity may be recreated meanwhile).
    LaunchedEffect(ui.captureUri) { ui.captureUri?.let { captureUri = it } }

    // Pause voice messages when the app goes to the background; release everything on leaving.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, ui) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                ui.voicePlayer.pause()
                if (ui.recording?.locked == false) ui.finishRecording(send = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (ui.recording != null) ui.finishRecording(send = false)
            ui.voicePlayer.release()
            ui.controller.close()
        }
    }

    // Live timer and waveform while recording.
    val rec = ui.recording
    LaunchedEffect(rec) {
        while (rec != null && ui.recording === rec) {
            rec.elapsedMs = ui.recorder.elapsedMs
            rec.addLevel(ui.recorder.level())
            delay(100)
        }
    }
    return ui
}


/** One media message in the list. */
@Composable
fun ChatMediaUi.MessageItem(message: ChatMessage, lang: AppLanguage, onReport: (ChatMessage) -> Unit) {
    val type = MediaType.fromWire(message.type) ?: return
    val media = message.media ?: return
    val transfers by controller.transfers.collectAsState()
    val localVersion by controller.localVersion.collectAsState()
    val voice by voicePlayer.state.collectAsState()
    val mine = isMine(message)
    val download = transfers[message.id]?.takeIf { it.kind == TransferInfo.Kind.DOWNLOAD }
    val hasLocal = remember(message.id, media.status, localVersion, download?.phase) { controller.hasLocalCopy(message, type) }
    val state = MediaUiLogic.forMessage(mine, media.status, hasLocal, download)
    val thumbPath = media.thumbPath
    val preview by produceState<Any?>(null, thumbPath, state) {
        value = if (thumbPath != null && (state == MediaUiState.REMOTE || state == MediaUiState.DOWNLOADING)) controller.thumbUrl(thumbPath) else null
    }
    val file = if (hasLocal) controller.localFile(message.id, type) else null
    val model = MediaBubbleModel(
        id = message.id,
        type = type,
        isMine = mine,
        state = state,
        progress = download?.progress ?: 0,
        caption = message.body,
        timestamp = message.timestamp,
        isRead = message.isRead,
        bytes = media.bytes,
        durationMs = media.durationMs,
        localFile = file,
        preview = preview,
        waiting = download?.phase == TransferInfo.Phase.WAITING
    )
    MediaBubble(
        model = model,
        lang = lang,
        voice = voice,
        actionsEnabled = !blocked,
        actions = MediaBubbleActions(
            onDownload = { if (!blocked) controller.download(message, type) },
            onCancelDownload = { controller.cancelDownload(message.id) },
            onOpen = {
                if (file != null) {
                    if (type == MediaType.IMAGE) viewerStart = message.id
                    if (type == MediaType.VIDEO) fullscreenVideo = file
                }
            },
            onToggleVoice = { if (file != null) voicePlayer.toggle(message.id, file) },
            onSeekVoice = { voicePlayer.seekTo(message.id, it) },
            onSpeed = { voicePlayer.cycleSpeed() },
            onSave = { if (file != null) save(file, type) },
            onReport = { onReport(message) }
        )
    )
}

/** A message still in the outbox: shown right away with progress, clock or retry. */
@Composable
fun ChatMediaUi.PendingItem(pending: PendingMedia, lang: AppLanguage) {
    val transfers by controller.transfers.collectAsState()
    val voice by voicePlayer.state.collectAsState()
    val transfer = transfers[pending.messageId]?.takeIf { it.kind == TransferInfo.Kind.SEND }
    val type = pending.mediaType
    val model = MediaBubbleModel(
        id = pending.messageId,
        type = type,
        isMine = true,
        state = MediaUiLogic.forPending(pending, transfer),
        progress = transfer?.progress ?: 0,
        caption = pending.caption.orEmpty(),
        bytes = pending.bytes,
        durationMs = pending.durationMs,
        preview = controller.outboxThumb(pending)?.takeIf { type == MediaType.VIDEO }
            ?: controller.outboxFile(pending).takeIf { type == MediaType.IMAGE },
        failureKey = pending.failureKey
    )
    MediaBubble(
        model = model,
        lang = lang,
        voice = voice,
        actionsEnabled = !blocked,
        actions = MediaBubbleActions(
            onCancelSend = { controller.cancelSend(pending.messageId) },
            onRetry = { if (!blocked) controller.retrySend(pending.messageId) }
        )
    )
}

/** "Preparing video..." and short notices above the input bar. */
@Composable
fun ChatMediaUi.Banners(lang: AppLanguage) {
    val t: (String) -> String = { Localization.t(it, lang) }
    val preparing by controller.preparing.collectAsState()
    val notice by controller.notice.collectAsState()
    preparing?.let { p ->
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().testTag("media_preparing")) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t(if (p.type == MediaType.VIDEO) "media_preparing_video" else "media_preparing_photo") + if (p.progress > 0) " ${p.progress}%" else "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                if (p.type == MediaType.VIDEO) {
                    IconButton(onClick = controller::cancelPreparing, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = t("media_cancel"))
                    }
                }
            }
        }
    }
    notice?.let { key ->
        LaunchedEffect(key) {
            delay(5_000)
            controller.clearNotice()
        }
        Surface(color = MaterialTheme.colorScheme.inverseSurface, modifier = Modifier.fillMaxWidth().clickable { controller.clearNotice() }.testTag("media_notice")) {
            Text(t(key), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

/** Caption, microphone explanation, photo viewer and full-screen video. */
@Composable
fun ChatMediaUi.Dialogs(messages: List<ChatMessage>, lang: AppLanguage, onReport: (ChatMessage) -> Unit) {
    val t: (String) -> String = { Localization.t(it, lang) }
    captionFor?.let { (uri, isVideo) ->
        CaptionDialog(uri, isVideo, lang, onDismiss = { captionFor = null }) { caption -> sendPicked(uri, isVideo, caption) }
    }
    if (askMic) {
        AlertDialog(
            onDismissRequest = { askMic = false },
            title = { Text(t("media_mic_title")) },
            text = { Text(t("media_mic_explain")) },
            confirmButton = {
                TextButton(onClick = { askMic = false; micPermission.launch(Manifest.permission.RECORD_AUDIO) }) { Text(t("media_mic_allow")) }
            },
            dismissButton = { TextButton(onClick = { askMic = false }) { Text(t("cancel")) } }
        )
    }
    viewerStart?.let { start ->
        val images = messages
            .filter { it.type == MediaType.IMAGE.wire && it.media != null && controller.hasLocalCopy(it, MediaType.IMAGE) }
            .map { it.id to controller.localFile(it.id, MediaType.IMAGE) }
        if (images.isEmpty()) {
            viewerStart = null
        } else {
            ImageViewerDialog(
                images = images,
                startId = start,
                lang = lang,
                canReport = { id -> messages.firstOrNull { it.id == id }?.let { !isMine(it) } == true },
                onSave = { save(it, MediaType.IMAGE) },
                onReport = { id -> messages.firstOrNull { it.id == id }?.let { viewerStart = null; onReport(it) } },
                onDismiss = { viewerStart = null }
            )
        }
    }
    fullscreenVideo?.let { file ->
        FullscreenVideoDialog(file, lang, onDismiss = { fullscreenVideo = null }, onBroken = {
            fullscreenVideo = null
            controller.showNotice("media_cannot_open")
        })
    }
}
