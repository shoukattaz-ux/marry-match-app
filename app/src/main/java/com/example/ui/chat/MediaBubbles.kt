package com.example.ui.chat

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.media.MediaType
import com.example.data.media.MediaUiLogic
import com.example.data.media.MediaUiState
import com.example.ui.theme.extendedColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Everything one media bubble shows; built by the chat screen from the message, outbox and transfers. */
data class MediaBubbleModel(
    val id: String,
    val type: MediaType,
    val isMine: Boolean,
    val state: MediaUiState,
    val progress: Int = 0,
    val caption: String = "",
    val timestamp: String = "",
    val isRead: Boolean = false,
    val bytes: Long? = null,
    val durationMs: Long? = null,
    /** The file on this phone (READY), or the file waiting in the outbox (sending). */
    val localFile: File? = null,
    /** Small preview: a local file or a signed link to the server preview. */
    val preview: Any? = null,
    /** Waiting for a connection or Wi-Fi before downloading. */
    val waiting: Boolean = false,
    /** Localization key of why sending stopped. */
    val failureKey: String? = null
)

class MediaBubbleActions(
    val onDownload: () -> Unit = {},
    val onCancelDownload: () -> Unit = {},
    val onCancelSend: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpen: () -> Unit = {},
    val onToggleVoice: () -> Unit = {},
    val onSeekVoice: (Float) -> Unit = {},
    val onSpeed: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onReport: () -> Unit = {}
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaBubble(
    model: MediaBubbleModel,
    lang: AppLanguage,
    voice: VoicePlayback,
    actionsEnabled: Boolean,
    actions: MediaBubbleActions
) {
    val t: (String) -> String = { Localization.t(it, lang) }
    var menu by remember { mutableStateOf(false) }
    var broken by remember(model.id, model.localFile) { mutableStateOf(false) }
    val voiceBroken = model.type == MediaType.VOICE && model.id in voice.broken
    val canSave = actionsEnabled && model.state == MediaUiState.READY && model.type != MediaType.VOICE && !broken
    val canReport = !model.isMine

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (model.isMine) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (model.isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(onClick = {}, onLongClick = { if (canSave || canReport) menu = true })
                    .testTag("media_bubble_${model.type.wire}")
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    when (model.type) {
                        MediaType.VOICE -> VoiceContent(model, lang, voice, actionsEnabled, actions)
                        else -> VisualContent(model, lang, actionsEnabled, actions, broken) { broken = true }
                    }
                    if (broken || voiceBroken) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(t("media_cannot_open"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            if (canReport) TextButton(onClick = actions.onReport) { Text(t("report")) }
                        }
                    }
                    if (model.caption.isNotBlank()) {
                        Text(
                            model.caption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor(model.isMine),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Footer(model)
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (canSave) {
                    DropdownMenuItem(
                        text = { Text(t("media_save_to_phone")) },
                        leadingIcon = { Icon(Icons.Default.SaveAlt, contentDescription = null) },
                        onClick = { menu = false; actions.onSave() }
                    )
                }
                if (canReport) {
                    DropdownMenuItem(
                        text = { Text(t("report"), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; actions.onReport() }
                    )
                }
            }
        }
    }
}

@Composable
private fun contentColor(isMine: Boolean) =
    if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun Footer(model: MediaBubbleModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val muted = if (model.isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.extendedColors.muted
        Text(model.timestamp, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = muted)
        if (model.isMine) {
            Spacer(Modifier.width(4.dp))
            val icon = when (model.state) {
                MediaUiState.QUEUED, MediaUiState.SENDING -> Icons.Default.Schedule
                MediaUiState.SEND_FAILED -> Icons.Default.ErrorOutline
                else -> if (model.isRead) Icons.Default.DoneAll else Icons.Default.Check
            }
            Icon(icon, contentDescription = null, tint = muted, modifier = Modifier.size(14.dp))
        }
    }
}

/** Photo or video: preview, state overlay, and the real picture or player once it is on the phone. */
@Composable
private fun VisualContent(
    model: MediaBubbleModel,
    lang: AppLanguage,
    actionsEnabled: Boolean,
    actions: MediaBubbleActions,
    broken: Boolean,
    onBroken: () -> Unit
) {
    val t: (String) -> String = { Localization.t(it, lang) }
    Box(
        modifier = Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        val ready = model.state == MediaUiState.READY && model.localFile != null
        when {
            ready && model.type == MediaType.IMAGE -> AsyncImage(
                model = model.localFile,
                contentDescription = t("media_photo"),
                contentScale = ContentScale.Crop,
                onError = { onBroken() },
                modifier = Modifier.fillMaxSize().clickable(enabled = !broken) { actions.onOpen() }
            )
            ready && model.type == MediaType.VIDEO -> InlineVideo(model.localFile!!, model.durationMs, onFullscreen = actions.onOpen, onBroken = onBroken)
            model.preview != null -> AsyncImage(
                model = model.preview,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Previews of received files stay blurred until downloaded; your own are shown as they are.
                modifier = Modifier.fillMaxSize().then(if (model.isMine) Modifier else Modifier.blur(18.dp))
            )
        }
        StateOverlay(model, lang, actionsEnabled, actions)
    }
}

/** Progress ring, download button, retry or expiry text in the middle of a photo, video or voice bubble. */
@Composable
private fun StateOverlay(model: MediaBubbleModel, lang: AppLanguage, actionsEnabled: Boolean, actions: MediaBubbleActions, compact: Boolean = false) {
    val t: (String) -> String = { Localization.t(it, lang) }
    val onDark = model.type != MediaType.VOICE
    val textColor = if (onDark) Color.White else contentColor(model.isMine)
    val bg = if (onDark) Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(8.dp) else Modifier
    when (model.state) {
        MediaUiState.SENDING, MediaUiState.DOWNLOADING -> ProgressWithCancel(
            model.progress, textColor,
            onCancel = if (model.state == MediaUiState.SENDING) actions.onCancelSend else actions.onCancelDownload,
            label = t("media_cancel")
        )
        MediaUiState.QUEUED -> Column(bg, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Schedule, contentDescription = t("media_waiting_network"), tint = textColor)
            if (!compact) Text(t("media_waiting_network"), color = textColor, style = MaterialTheme.typography.labelSmall)
            IconButton(onClick = actions.onCancelSend, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = t("media_cancel"), tint = textColor)
            }
        }
        MediaUiState.SEND_FAILED -> Column(
            bg.clickable(enabled = actionsEnabled) { actions.onRetry() }.testTag("media_retry"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = textColor)
            Text(t("media_failed_retry"), color = textColor, style = MaterialTheme.typography.labelSmall)
            model.failureKey?.takeIf { it != "media_send_failed" }?.let {
                Text(t(it), color = textColor, style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = actions.onCancelSend) { Text(t("media_discard"), color = textColor) }
        }
        MediaUiState.REMOTE -> if (actionsEnabled) {
            Column(
                bg.clickable { actions.onDownload() }.testTag("media_download"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Download, contentDescription = t("media_download"), tint = textColor)
                Text(
                    listOfNotNull(
                        MediaUiLogic.formatBytes(model.bytes).ifBlank { null },
                        model.durationMs?.takeIf { model.type != MediaType.IMAGE }?.let { MediaUiLogic.formatDuration(it) }
                    ).joinToString(" · "),
                    color = textColor, style = MaterialTheme.typography.labelSmall
                )
                if (model.waiting) Text(t("media_waiting_network"), color = textColor, style = MaterialTheme.typography.labelSmall)
            }
        }
        MediaUiState.EXPIRED -> Text(t("media_expired"), color = textColor, style = MaterialTheme.typography.labelSmall, modifier = bg)
        MediaUiState.GONE -> Text(t("media_gone"), color = textColor, style = MaterialTheme.typography.labelSmall, modifier = bg)
        MediaUiState.READY -> {}
    }
}

@Composable
private fun ProgressWithCancel(progress: Int, color: Color, onCancel: () -> Unit, label: String) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape)) {
        if (progress in 1..99) {
            CircularProgressIndicator(progress = { progress / 100f }, color = Color.White, modifier = Modifier.size(46.dp))
        } else {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(46.dp))
        }
        IconButton(onClick = onCancel, modifier = Modifier.size(36.dp).testTag("media_cancel")) {
            Icon(Icons.Default.Close, contentDescription = label, tint = color.takeIf { it != Color.Unspecified } ?: Color.White)
        }
    }
}

/** Voice message: play/pause, seek bar, duration, speed. Only plays the file on this phone. */
@Composable
private fun VoiceContent(model: MediaBubbleModel, lang: AppLanguage, voice: VoicePlayback, actionsEnabled: Boolean, actions: MediaBubbleActions) {
    val t: (String) -> String = { Localization.t(it, lang) }
    val color = contentColor(model.isMine)
    val isCurrent = voice.messageId == model.id
    val total = (if (isCurrent && voice.durationMs > 0) voice.durationMs else model.durationMs ?: 0L).coerceAtLeast(1)
    Row(
        modifier = Modifier.width(260.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            if (model.state == MediaUiState.READY) {
                IconButton(onClick = actions.onToggleVoice, modifier = Modifier.testTag("voice_play")) {
                    Icon(
                        if (isCurrent && voice.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = t(if (isCurrent && voice.playing) "media_pause" else "media_play"),
                        tint = color
                    )
                }
            } else {
                StateOverlay(model, lang, actionsEnabled, actions, compact = true)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Slider(
                value = if (isCurrent) (voice.positionMs.toFloat() / total).coerceIn(0f, 1f) else 0f,
                onValueChange = { actions.onSeekVoice(it) },
                enabled = isCurrent && model.state == MediaUiState.READY,
                modifier = Modifier.height(28.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val shown = if (isCurrent && voice.positionMs > 0) voice.positionMs else model.durationMs
                Text(MediaUiLogic.formatDuration(shown), style = MaterialTheme.typography.labelSmall, color = color)
                if (!model.isMine && model.state == MediaUiState.READY && model.id !in voice.listened) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                }
                if (model.state == MediaUiState.REMOTE && model.bytes != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(MediaUiLogic.formatBytes(model.bytes), style = MaterialTheme.typography.labelSmall, color = color)
                }
                if (model.state == MediaUiState.EXPIRED || model.state == MediaUiState.GONE) {
                    Spacer(Modifier.width(6.dp))
                    Text(t(if (model.state == MediaUiState.EXPIRED) "media_expired" else "media_gone"), style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
        }
        if (isCurrent && model.state == MediaUiState.READY) {
            TextButton(onClick = actions.onSpeed, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text("${if (voice.speed % 1f == 0f) voice.speed.toInt().toString() else voice.speed.toString()}x", color = color, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** A downloaded video in the chat: poster with play button; plays muted inline, tap for full screen. */
@Composable
private fun InlineVideo(file: File, durationMs: Long?, onFullscreen: () -> Unit, onBroken: () -> Unit) {
    var playing by remember(file) { mutableStateOf(false) }
    val poster by produceState<Bitmap?>(null, file) { value = withContext(Dispatchers.IO) { firstFrame(file) } }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (playing) {
            LocalVideoPlayer(
                file = file, muted = true, showControls = false, onBroken = onBroken, onEnded = { playing = false },
                modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onFullscreen() } }
            )
            IconButton(onClick = onFullscreen, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.Fullscreen, contentDescription = null, tint = Color.White)
            }
        } else {
            poster?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            IconButton(
                onClick = { playing = true },
                modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).testTag("video_play")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            durationMs?.let {
                Text(
                    MediaUiLogic.formatDuration(it),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 4.dp)
                )
            }
        }
    }
}

private fun firstFrame(file: File): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        retriever.getFrameAtTime(0)
    } catch (e: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/** ExoPlayer for a file on this phone; never streams from the server. */
@Composable
fun LocalVideoPlayer(
    file: File,
    muted: Boolean,
    showControls: Boolean,
    onBroken: () -> Unit,
    onEnded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            volume = if (muted) 0f else 1f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = onBroken()
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = showControls
                this.player = player
            }
        },
        modifier = modifier
    )
}

/** Full-screen video with sound and controls. */
@Composable
fun FullscreenVideoDialog(file: File, lang: AppLanguage, onDismiss: () -> Unit, onBroken: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            LocalVideoPlayer(file = file, muted = false, showControls = true, onBroken = onBroken, modifier = Modifier.fillMaxSize())
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.Default.Close, contentDescription = Localization.t("close", lang), tint = Color.White)
            }
        }
    }
}

/** Full-screen photos of the chat: swipe between them, pinch to zoom, save or report. */
@Composable
fun ImageViewerDialog(
    images: List<Pair<String, File>>,
    startId: String,
    lang: AppLanguage,
    canReport: (String) -> Boolean,
    onSave: (File) -> Unit,
    onReport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val t: (String) -> String = { Localization.t(it, lang) }
    val start = images.indexOfFirst { it.first == startId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { images.size }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableImage(images[page].second)
            }
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = t("close"), tint = Color.White) }
                Spacer(Modifier.weight(1f))
                val current = images.getOrNull(pager.currentPage)
                if (current != null) {
                    TextButton(onClick = { onSave(current.second) }, modifier = Modifier.testTag("viewer_save")) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(t("media_save_to_phone"), color = Color.White)
                    }
                    if (canReport(current.first)) {
                        IconButton(onClick = { onReport(current.first) }) {
                            Icon(Icons.Default.Flag, contentDescription = t("report"), tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(file: File) {
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offsetX by remember(file) { mutableFloatStateOf(0f) }
    var offsetY by remember(file) { mutableFloatStateOf(0f) }
    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(file) {
                detectTapGestures(onDoubleTap = { scale = 1f; offsetX = 0f; offsetY = 0f })
            }
            .pointerInput(file) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
    )
}
