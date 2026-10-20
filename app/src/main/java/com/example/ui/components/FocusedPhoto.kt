package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.model.PhotoFocus
import com.example.ui.theme.extendedColors

/**
 * A photo filling its (clipped) parent with the saved framing: [PhotoFocus.x]/[PhotoFocus.y] pick
 * which part of the photo is shown, [PhotoFocus.zoom] enlarges around that point. Zooming around
 * a point inside the frame keeps the frame covered, so no empty edge can appear.
 */
@Composable
fun FocusedPhoto(model: Any?, focus: PhotoFocus, contentDescription: String?, modifier: Modifier = Modifier) {
    val f = focus.clamped()
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(f.x, f.y),
        modifier = modifier.graphicsLayer {
            scaleX = f.zoom
            scaleY = f.zoom
            transformOrigin = TransformOrigin((f.x + 1f) / 2f, (f.y + 1f) / 2f)
        }
    )
}

/** "Adjust photo": drag to move and pinch or slide to zoom, so the face sits in the middle of the circle. */
@Composable
fun AvatarAdjustDialog(
    photo: Any?,
    initial: PhotoFocus,
    lang: AppLanguage,
    saving: Boolean,
    errorText: String?,
    onDismiss: () -> Unit,
    onSave: (PhotoFocus) -> Unit,
    onChangePhotos: () -> Unit
) {
    val t: (String) -> String = { Localization.t(it, lang) }
    var focus by remember(initial) { mutableStateOf(initial.clamped()) }
    var sizePx by remember { mutableFloatStateOf(0f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("avatar_adjust_title")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("avatar_adjust_hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted, textAlign = TextAlign.Center)
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .onSizeChanged { sizePx = it.width.toFloat() }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                focus = focus.zoomedBy(zoomChange).dragged(pan.x, pan.y, sizePx)
                            }
                        }
                        .testTag("avatar_adjust_preview")
                ) {
                    FocusedPhoto(photo, focus, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(t("avatar_zoom"), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = focus.zoom,
                        onValueChange = { focus = focus.copy(zoom = it).clamped() },
                        valueRange = PhotoFocus.MIN_ZOOM..PhotoFocus.MAX_ZOOM,
                        modifier = Modifier.weight(1f).testTag("avatar_zoom_slider")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { focus = PhotoFocus() }) { Text(t("avatar_reset")) }
                    TextButton(onClick = onChangePhotos, modifier = Modifier.testTag("avatar_change_photos")) { Text(t("avatar_change_photos")) }
                }
                if (errorText != null) {
                    Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(focus) }, enabled = !saving, modifier = Modifier.testTag("avatar_save")) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(t("save"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
    )
}
