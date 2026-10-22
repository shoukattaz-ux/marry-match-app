package com.example.ui.chat

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** What moves over a theme's background. */
enum class ThemeParticle { NONE, HEARTS, STARS, BUBBLES, PETALS, SPARKLES }

/**
 * A chat theme: background ([gradient], [particle]s and a few fixed shapes) and the bubble
 * colours that read well on it. [key] is what the server stores (chat_themes.theme).
 */
data class ChatTheme(
    val key: String,
    val gradient: List<Color>,
    val particle: ThemeParticle,
    val particleColors: List<Color>,
    val myBubble: Color,
    val onMyBubble: Color,
    val theirBubble: Color,
    val onTheirBubble: Color,
    /** Draws a moon (lofi, starry), a sun (sunset) or a city skyline (lofi). */
    val moon: Boolean = false,
    val sun: Boolean = false,
    val skyline: Boolean = false
) {
    val isClassic: Boolean get() = key == ChatThemes.CLASSIC
}

object ChatThemes {
    const val CLASSIC = "classic"

    val ALL = listOf(
        ChatTheme(
            CLASSIC, emptyList(), ThemeParticle.NONE, emptyList(),
            myBubble = Color.Unspecified, onMyBubble = Color.Unspecified,
            theirBubble = Color.Unspecified, onTheirBubble = Color.Unspecified
        ),
        ChatTheme(
            "hearts", listOf(Color(0xFF8E1D4F), Color(0xFFC2185B), Color(0xFFE85A8E)), ThemeParticle.HEARTS,
            listOf(Color(0xFFFF8FB8), Color(0xFFFFC1D6), Color(0xFFFF4D8D)),
            myBubble = Color(0xFFFFFFFF), onMyBubble = Color(0xFF8E1D4F),
            theirBubble = Color(0xFF5C1236), onTheirBubble = Color(0xFFFFE3EE)
        ),
        ChatTheme(
            "lofi", listOf(Color(0xFF120B3A), Color(0xFF2B1B6B), Color(0xFF5B3FB0)), ThemeParticle.STARS,
            listOf(Color(0xFFFFFFFF), Color(0xFFB9A8FF)),
            myBubble = Color(0xFF7C5CFF), onMyBubble = Color.White,
            theirBubble = Color(0xFF2A2156), onTheirBubble = Color(0xFFE8E2FF),
            moon = true, skyline = true
        ),
        ChatTheme(
            "starry", listOf(Color(0xFF050A1F), Color(0xFF0D1B3D), Color(0xFF1B2F5E)), ThemeParticle.STARS,
            listOf(Color(0xFFFFFFFF), Color(0xFFFFE9A8), Color(0xFFA8D4FF)),
            myBubble = Color(0xFF3D6FD8), onMyBubble = Color.White,
            theirBubble = Color(0xFF16264A), onTheirBubble = Color(0xFFE3ECFF),
            moon = true
        ),
        ChatTheme(
            "ocean", listOf(Color(0xFF003B57), Color(0xFF00708A), Color(0xFF28A7B8)), ThemeParticle.BUBBLES,
            listOf(Color(0xFFBFF4FF), Color(0xFFFFFFFF)),
            myBubble = Color(0xFFFFFFFF), onMyBubble = Color(0xFF00485F),
            theirBubble = Color(0xFF00546B), onTheirBubble = Color(0xFFE0FAFF)
        ),
        ChatTheme(
            "sunset", listOf(Color(0xFF3A1C5C), Color(0xFFC2416B), Color(0xFFFF9A4D)), ThemeParticle.SPARKLES,
            listOf(Color(0xFFFFE0A3), Color(0xFFFFFFFF)),
            myBubble = Color(0xFFFFFFFF), onMyBubble = Color(0xFF8A2B4E),
            theirBubble = Color(0xFF4A2160), onTheirBubble = Color(0xFFFFE9DD),
            sun = true
        ),
        ChatTheme(
            "garden", listOf(Color(0xFF0F3D2E), Color(0xFF2E7D4F), Color(0xFF7CC47F)), ThemeParticle.PETALS,
            listOf(Color(0xFFFFC1D9), Color(0xFFFFFFFF), Color(0xFFFFE08A)),
            myBubble = Color(0xFFFFFFFF), onMyBubble = Color(0xFF1E5A3A),
            theirBubble = Color(0xFF184D36), onTheirBubble = Color(0xFFE6F7EA)
        ),
        ChatTheme(
            "golden", listOf(Color(0xFF0F1A2A), Color(0xFF1E3A5F), Color(0xFF2B4D78)), ThemeParticle.SPARKLES,
            listOf(Color(0xFFE9B44C), Color(0xFFF7D98B)),
            myBubble = Color(0xFFE9B44C), onMyBubble = Color(0xFF1D2433),
            theirBubble = Color(0xFF223A5C), onTheirBubble = Color(0xFFF1F4F9)
        )
    )

    fun find(key: String?): ChatTheme = ALL.firstOrNull { it.key == key } ?: ALL.first()
}

/** One moving thing: where it starts (0..1), how fast, how big and its colour. */
private class Particle(val x: Float, val y: Float, val speed: Float, val size: Float, val phase: Float, val color: Int)

/**
 * The theme's background, filling its parent. Moves slowly in a loop; stands still when the
 * phone has animations switched off (accessibility) or [animate] is false (small previews).
 */
@Composable
fun ChatThemeBackground(theme: ChatTheme, modifier: Modifier = Modifier, animate: Boolean = true) {
    if (theme.isClassic) return
    val context = LocalContext.current
    val motion = animate && remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
            .getOrDefault(1f) > 0f
    }
    val particles = remember(theme.key) {
        val random = Random(theme.key.hashCode())
        val count = when (theme.particle) {
            ThemeParticle.STARS -> 60
            ThemeParticle.NONE -> 0
            else -> 18
        }
        List(count) {
            Particle(
                x = random.nextFloat(), y = random.nextFloat(), speed = 0.6f + random.nextFloat() * 0.8f,
                size = 0.5f + random.nextFloat(), phase = random.nextFloat(), color = random.nextInt(theme.particleColors.size.coerceAtLeast(1))
            )
        }
    }
    val time = if (motion) {
        val loop = rememberInfiniteTransition(label = "chat_theme")
        val t by loop.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 24_000, easing = LinearEasing), RepeatMode.Restart),
            label = "chat_theme_time"
        )
        t
    } else 0.35f

    Box(modifier.fillMaxSize().background(Brush.verticalGradient(theme.gradient))) {
        Canvas(Modifier.fillMaxSize()) {
            if (theme.sun) drawSun()
            if (theme.moon) drawMoon()
            particles.forEach { drawParticle(theme, it, time) }
            if (theme.skyline) drawSkyline(time)
        }
    }
}

private fun DrawScope.drawParticle(theme: ChatTheme, p: Particle, time: Float) {
    val color = theme.particleColors.getOrElse(p.color) { Color.White }
    val w = size.width
    val h = size.height
    val unit = minOf(w, h) / 40f
    // Loops: each particle crosses the screen a whole number of times per animation cycle.
    val cycle = (time * p.speed * 3f + p.phase) % 1f
    when (theme.particle) {
        ThemeParticle.HEARTS -> {
            val y = h * (1.1f - cycle * 1.25f)
            val x = w * p.x + sin((cycle + p.phase) * 2 * PI).toFloat() * unit * 2.5f
            val s = unit * (1.2f + p.size * 1.3f)
            drawHeart(Offset(x, y), s, color.copy(alpha = 0.55f * fade(cycle)))
        }
        ThemeParticle.STARS -> {
            val twinkle = 0.25f + 0.75f * abs(sin((time * p.speed * 6f + p.phase) * PI).toFloat())
            // Stars stay in the upper part when a skyline is drawn below.
            val y = h * p.y * (if (theme.skyline) 0.7f else 1f)
            drawCircle(color.copy(alpha = twinkle * 0.9f), radius = unit * 0.12f * (1f + p.size), center = Offset(w * p.x, y))
        }
        ThemeParticle.BUBBLES -> {
            val y = h * (1.1f - cycle * 1.25f)
            val x = w * p.x + sin((cycle * 3 + p.phase) * 2 * PI).toFloat() * unit
            val r = unit * (0.5f + p.size * 0.9f)
            drawCircle(color.copy(alpha = 0.45f * fade(cycle)), radius = r, center = Offset(x, y), style = Stroke(width = unit * 0.15f))
            drawCircle(Color.White.copy(alpha = 0.35f * fade(cycle)), radius = r * 0.25f, center = Offset(x - r * 0.35f, y - r * 0.35f))
        }
        ThemeParticle.PETALS -> {
            val y = h * (-0.1f + cycle * 1.25f)
            val x = w * p.x + sin((cycle * 2 + p.phase) * 2 * PI).toFloat() * unit * 3f
            val s = unit * (0.6f + p.size * 0.6f)
            rotate(degrees = (cycle * 360f * p.speed + p.phase * 360f), pivot = Offset(x, y)) {
                drawOval(color.copy(alpha = 0.7f * fade(cycle)), topLeft = Offset(x - s, y - s * 0.5f), size = Size(s * 2f, s))
            }
        }
        ThemeParticle.SPARKLES -> {
            // Drifts slowly upwards and twinkles.
            val twinkle = abs(sin((time * p.speed * 5f + p.phase) * PI).toFloat())
            drawSparkle(Offset(w * p.x, h * ((p.y + 1f - cycle * 0.2f) % 1f)), unit * (0.35f + p.size * 0.45f), color.copy(alpha = 0.85f * twinkle))
        }
        ThemeParticle.NONE -> Unit
    }
}

/** Fades in at the start and out at the end of a crossing. */
private fun fade(cycle: Float): Float = (minOf(cycle, 1f - cycle) * 6f).coerceIn(0f, 1f)

private fun DrawScope.drawHeart(center: Offset, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + s * 0.9f)
        cubicTo(center.x - s * 1.6f, center.y - s * 0.1f, center.x - s * 0.7f, center.y - s * 1.2f, center.x, center.y - s * 0.35f)
        cubicTo(center.x + s * 0.7f, center.y - s * 1.2f, center.x + s * 1.6f, center.y - s * 0.1f, center.x, center.y + s * 0.9f)
        close()
    }
    drawPath(path, color)
}

/** A four-pointed glint. */
private fun DrawScope.drawSparkle(center: Offset, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - s)
        quadraticBezierTo(center.x, center.y, center.x + s, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y + s)
        quadraticBezierTo(center.x, center.y, center.x - s, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y - s)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawMoon() {
    val r = minOf(size.width, size.height) * 0.07f
    val c = Offset(size.width * 0.78f, size.height * 0.12f)
    drawCircle(Color(0xFFFFF4D6).copy(alpha = 0.12f), radius = r * 2.2f, center = c)
    drawCircle(Color(0xFFFFF4D6), radius = r, center = c)
    // A crescent: cover part of the moon with the sky colour above it.
    drawCircle(Color(0xFF1B1550).copy(alpha = 0.85f), radius = r * 0.9f, center = Offset(c.x + r * 0.45f, c.y - r * 0.25f))
}

private fun DrawScope.drawSun() {
    val r = minOf(size.width, size.height) * 0.16f
    val c = Offset(size.width * 0.5f, size.height * 0.92f)
    drawCircle(Color(0xFFFFD27A).copy(alpha = 0.18f), radius = r * 2.4f, center = c)
    drawCircle(Color(0xFFFFC15E).copy(alpha = 0.35f), radius = r * 1.5f, center = c)
    drawCircle(Color(0xFFFFB347), radius = r, center = c)
}

/** A city at night along the bottom, with windows that light up and go dark. */
private fun DrawScope.drawSkyline(time: Float) {
    val w = size.width
    val h = size.height
    val random = Random(7)
    var x = 0f
    var i = 0
    while (x < w) {
        val bw = w * (0.08f + random.nextFloat() * 0.08f)
        val bh = h * (0.08f + random.nextFloat() * 0.14f)
        drawRect(Color(0xFF0C0828).copy(alpha = 0.92f), topLeft = Offset(x, h - bh), size = Size(bw, bh))
        val win = bw / 6f
        var wy = h - bh + win
        while (wy < h - win) {
            var wx = x + win
            while (wx < x + bw - win) {
                val on = sin((time * 2f + (i * 0.37f)) * 2 * PI).toFloat() > 0.2f - random.nextFloat()
                if (on) drawRect(Color(0xFFFFD98A).copy(alpha = 0.75f), topLeft = Offset(wx, wy), size = Size(win * 0.7f, win * 0.9f))
                wx += win * 1.6f
                i++
            }
            wy += win * 1.8f
        }
        x += bw + w * 0.01f
    }
}
