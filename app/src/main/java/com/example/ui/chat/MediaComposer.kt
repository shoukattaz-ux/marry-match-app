package com.example.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.media.ChatMediaRules
import com.example.data.media.MediaUiLogic
import java.io.File
import java.util.UUID

/** Camera video with the 30-second limit passed to the camera app. */
class CaptureShortVideo : ActivityResultContracts.CaptureVideo() {
    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input).apply {
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, (ChatMediaRules.MAX_VIDEO_MS / 1000).toInt())
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
        }
}

/** A file in cache/chat_capture/ the camera app may write to (FileProvider, see chat_media_paths.xml). */
fun newCaptureUri(context: Context, extension: String): Uri {
    val dir = File(context.cacheDir, "chat_capture").apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}.$extension")
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

enum class AttachChoice { PHOTO_GALLERY, PHOTO_CAMERA, VIDEO_GALLERY, VIDEO_CAMERA }

@Composable
fun AttachButton(lang: AppLanguage, enabled: Boolean, onChoose: (AttachChoice) -> Unit) {
    val t: (String) -> String = { Localization.t(it, lang) }
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.testTag("chat_attach_button")) {
            Icon(Icons.Default.AttachFile, contentDescription = t("media_attach"))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                Triple(AttachChoice.PHOTO_GALLERY, Icons.Default.PhotoLibrary, "media_pick_photo"),
                Triple(AttachChoice.PHOTO_CAMERA, Icons.Default.PhotoCamera, "media_take_photo"),
                Triple(AttachChoice.VIDEO_GALLERY, Icons.Default.VideoLibrary, "media_pick_video"),
                Triple(AttachChoice.VIDEO_CAMERA, Icons.Default.Videocam, "media_record_video")
            ).forEach { (choice, icon, key) ->
                DropdownMenuItem(
                    text = { Text(t(key)) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    onClick = { open = false; onChoose(choice) }
                )
            }
        }
    }
}

/** Preview of the picked photo or video with an optional caption (max 300 characters). */
@Composable
fun CaptionDialog(uri: Uri, isVideo: Boolean, lang: AppLanguage, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    val t: (String) -> String = { Localization.t(it, lang) }
    var caption by remember(uri) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t(if (isVideo) "media_send_video" else "media_send_photo")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isVideo) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    )
                } else {
                    Text(t("media_video_hint"), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it.take(ChatMediaRules.MAX_CAPTION) },
                    label = { Text(t("media_caption")) },
                    supportingText = { Text("${caption.length} / ${ChatMediaRules.MAX_CAPTION}") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("media_caption_field")
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSend(caption) }, modifier = Modifier.testTag("media_send_confirm")) { Text(t("send")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
    )
}

/** Live state of a voice recording shown in the input bar. */
class VoiceRecording(val messageId: String, val file: File) {
    var locked by mutableStateOf(false)
    var elapsedMs by mutableLongStateOf(0L)
    var dragX by mutableFloatStateOf(0f)
    val levels = mutableStateListOf<Float>()

    fun addLevel(level: Float) {
        levels.add(level)
        if (levels.size > MAX_BARS) levels.removeAt(0)
    }

    companion object {
        const val MAX_BARS = 40
    }
}

/**
 * Hold to record, slide left to cancel, slide up to lock hands-free recording.
 * [onPress] returns false when recording cannot start (e.g. no microphone permission yet).
 */
@Composable
fun MicButton(
    lang: AppLanguage,
    enabled: Boolean,
    onPress: () -> Boolean,
    onDrag: (Float) -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
    onLock: () -> Unit
) {
    val density = LocalDensity.current
    val cancelPx = with(density) { 110.dp.toPx() }
    val lockPx = with(density) { 90.dp.toPx() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .testTag("chat_mic_button")
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!onPress()) return@awaitEachGesture
                    var total = Offset.Zero
                    var result = 0 // 0 release, 1 cancel, 2 lock
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        total += change.positionChange()
                        change.consume()
                        onDrag(total.x.coerceAtMost(0f))
                        if (total.x < -cancelPx) { result = 1; break }
                        if (total.y < -lockPx) { result = 2; break }
                    }
                    when (result) {
                        1 -> onCancel()
                        2 -> onLock()
                        else -> onRelease()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Mic, contentDescription = Localization.t("media_hold_to_record", lang), tint = Color.White)
    }
}

/** Replaces the text field while recording: timer, live waveform, slide hint or (locked) cancel and send. */
@Composable
fun RecordingBar(recording: VoiceRecording, lang: AppLanguage, onCancel: () -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier) {
    val t: (String) -> String = { Localization.t(it, lang) }
    val barColor = MaterialTheme.colorScheme.primary
    Row(modifier = modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(Color.Red, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(MediaUiLogic.formatDuration(recording.elapsedMs), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("recording_timer"))
        Spacer(Modifier.width(8.dp))
        Canvas(modifier = Modifier.weight(1f).height(32.dp)) {
            val bars = recording.levels
            if (bars.isEmpty()) return@Canvas
            val step = size.width / VoiceRecording.MAX_BARS
            bars.forEachIndexed { i, level ->
                val h = (size.height * (0.1f + 0.9f * level)).coerceAtMost(size.height)
                val x = i * step + step / 2
                drawLine(barColor, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), strokeWidth = step * 0.6f)
            }
        }
        if (recording.locked) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.Delete, contentDescription = t("media_cancel"), tint = MaterialTheme.colorScheme.error) }
            IconButton(
                onClick = onSend,
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape).testTag("recording_send")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = t("send"), tint = Color.White)
            }
        } else {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null)
            Text(t("media_slide_to_cancel"), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.Lock, contentDescription = t("media_slide_up_lock"), modifier = Modifier.size(16.dp))
        }
    }
}
