package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
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

/** Sizes of the photos on a Discover card, so previews show exactly the same crop. */
object CardPhotoFrame {
    val MAIN_HEIGHT = 420.dp
    val EXTRA_HEIGHT = 380.dp
    /** Width / height on a typical phone (card width about 320 dp). */
    const val MAIN_ASPECT = 0.76f
    const val EXTRA_ASPECT = 0.84f
}

/**
 * "Adjust photo": drag to move and pinch or slide to zoom. The frame is shaped like the photo on
 * a Discover card ([isMain] adds the name line and the round profile picture), so what you see
 * here is what other members see.
 */
@Composable
fun PhotoAdjustDialog(
    photo: Any?,
    initial: PhotoFocus,
    lang: AppLanguage,
    isMain: Boolean,
    caption: String,
    saving: Boolean,
    errorText: String?,
    onDismiss: () -> Unit,
    onSave: (PhotoFocus) -> Unit,
    onChangePhotos: () -> Unit
) {
    val t: (String) -> String = { Localization.t(it, lang) }
    var focus by remember(initial) { mutableStateOf(initial.clamped()) }
    var frameSize by remember { mutableStateOf(IntSize.Zero) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("photo_adjust_title")) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(t("photo_adjust_hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted, textAlign = TextAlign.Center)
                Box(
                    modifier = Modifier
                        .width(220.dp)
                        .aspectRatio(if (isMain) CardPhotoFrame.MAIN_ASPECT else CardPhotoFrame.EXTRA_ASPECT)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .onSizeChanged { frameSize = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                focus = focus.zoomedBy(zoomChange)
                                    .dragged(pan.x, pan.y, frameSize.width.toFloat(), frameSize.height.toFloat())
                            }
                        }
                        .testTag("photo_adjust_preview")
                ) {
                    FocusedPhoto(photo, focus, contentDescription = null, modifier = Modifier.fillMaxSize())
                    if (isMain) {
                        // The name line sits on the photo on Discover, so the face must stay above it.
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        Text(
                            caption,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                        )
                    }
                }
                Text(t(if (isMain) "photo_adjust_card_main" else "photo_adjust_card_extra"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.extendedColors.muted)
                if (isMain) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .testTag("photo_adjust_circle")
                        ) {
                            FocusedPhoto(photo, focus, contentDescription = null, modifier = Modifier.fillMaxSize())
                        }
                        Text(t("photo_adjust_circle"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.extendedColors.muted)
                    }
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
