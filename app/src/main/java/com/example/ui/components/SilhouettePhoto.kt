package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag

/**
 * Shown instead of the photos of a woman who chose a silhouette profile (migration 049): a
 * soft grey gradient with a woman's outline (hair with a bun, head, neck, shoulders). Her real
 * photos are shown to her matches only. Drawn in code, so it is sharp at every size.
 */
@Composable
fun SilhouettePhoto(modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(Color(0xFF6E6E6E), Color(0xFF4A4A4A), Color(0xFF2B2B2B))))
            .testTag("silhouette_photo")
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val shape = Color(0xFF3A3A3A)
            val w = size.width
            val h = size.height
            val cx = w / 2f
            // Scale to the card: the figure fills the lower 80 %.
            val unit = minOf(w, h * 0.75f)
            val headW = unit * 0.36f
            val headH = headW * 1.22f
            val headTop = h * 0.30f
            // Hair: a fuller oval behind the head, a bun on top and locks to the shoulders.
            drawOval(shape, Offset(cx - headW * 0.66f, headTop - headH * 0.12f), Size(headW * 1.32f, headH * 1.18f))
            drawOval(shape, Offset(cx - headW * 0.30f, headTop - headH * 0.38f), Size(headW * 0.60f, headH * 0.42f))
            val locks = Path().apply {
                moveTo(cx - headW * 0.62f, headTop + headH * 0.45f)
                quadraticBezierTo(cx - headW * 0.80f, headTop + headH * 1.05f, cx - headW * 0.52f, headTop + headH * 1.30f)
                lineTo(cx + headW * 0.52f, headTop + headH * 1.30f)
                quadraticBezierTo(cx + headW * 0.80f, headTop + headH * 1.05f, cx + headW * 0.62f, headTop + headH * 0.45f)
                close()
            }
            drawPath(locks, shape)
            // Head and neck.
            drawOval(shape, Offset(cx - headW / 2f, headTop), Size(headW, headH))
            drawRect(shape, Offset(cx - headW * 0.20f, headTop + headH * 0.85f), Size(headW * 0.40f, headH * 0.45f))
            // Shoulders down to the bottom edge.
            val shoulderTop = headTop + headH * 1.20f
            val body = Path().apply {
                moveTo(cx - headW * 0.22f, shoulderTop)
                cubicTo(cx - headW * 0.95f, shoulderTop + headH * 0.05f, cx - headW * 1.55f, shoulderTop + headH * 0.30f, cx - headW * 1.70f, h)
                lineTo(cx + headW * 1.70f, h)
                cubicTo(cx + headW * 1.55f, shoulderTop + headH * 0.30f, cx + headW * 0.95f, shoulderTop + headH * 0.05f, cx + headW * 0.22f, shoulderTop)
                close()
            }
            drawPath(body, shape)
        }
    }
}
