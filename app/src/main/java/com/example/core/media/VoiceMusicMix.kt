package com.example.core.media

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Background music under a voice message: the part [startMs]..[endMs] of a song, at [volume]
 * (0.05..1.0 of its own loudness) while the voice stays at 100 %. [songFile] is the app's own
 * copy of the song (the original may move or lose its permission).
 */
data class VoiceMusic(
    val songFile: String,
    val songName: String,
    val startMs: Long,
    val endMs: Long,
    val volume: Float
) {
    val lengthMs: Long get() = endMs - startMs

    companion object {
        const val MIN_VOLUME = 0.05f
        const val MAX_VOLUME = 1f
        const val DEFAULT_VOLUME = 0.15f
        /** The quick choices shown above the slider. */
        val PRESETS = listOf(0.10f, 0.15f, 0.30f, 0.50f, 0.60f)
        /** Shortest part of a song that can be chosen. */
        const val MIN_LENGTH_MS = 2_000L
    }
}

/**
 * The sound maths of mixing (no Android parts, so it is unit tested): everything is mono 16-bit
 * PCM at one sample rate. The voice keeps its full level; the music loops to the voice's length,
 * fades in and out, and is scaled to the chosen volume. Peaks are softened so loud moments do not
 * crackle.
 */
object VoiceMusicMath {
    /** Sample rate of a mixed message: clear enough for music, small enough for chat. */
    const val MIX_RATE = 32_000
    const val FADE_IN_MS = 600L
    const val FADE_OUT_MS = 1_000L
    /** Where peaks start to be softened (a share of full scale). */
    private const val SOFT_KNEE = 0.85f

    /**
     * [voice] at 100 % plus [music] at [volume], as long as the voice. Short music repeats
     * (the joins are smoothed); long music is cut with a fade-out at the end.
     */
    fun mix(voice: ShortArray, music: ShortArray, rate: Int, volume: Float): ShortArray {
        if (voice.isEmpty()) return ShortArray(0)
        if (music.isEmpty()) return voice.copyOf()
        val gain = volume.coerceIn(VoiceMusic.MIN_VOLUME, VoiceMusic.MAX_VOLUME)
        val fadeIn = (rate * FADE_IN_MS / 1000).toInt().coerceAtMost(voice.size / 2).coerceAtLeast(1)
        val fadeOut = (rate * FADE_OUT_MS / 1000).toInt().coerceAtMost(voice.size / 2).coerceAtLeast(1)
        // Joins of a repeating part fade over 30 ms so they do not click.
        val join = min(rate * 30 / 1000, music.size / 4).coerceAtLeast(1)
        val out = ShortArray(voice.size)
        for (i in voice.indices) {
            val m = i % music.size
            var musicLevel = music[m] / 32768f
            if (music.size < voice.size) musicLevel *= joinGain(m, music.size, join)
            val envelope = min(min(1f, i.toFloat() / fadeIn), min(1f, (voice.size - 1 - i).toFloat() / fadeOut))
            val sum = voice[i] / 32768f + musicLevel * gain * envelope
            out[i] = (soften(sum) * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** Fades the first and last [join] samples of one repeat of the music. */
    private fun joinGain(position: Int, length: Int, join: Int): Float =
        min(1f, min(position.toFloat() / join, (length - 1 - position).toFloat() / join))

    /** Leaves normal levels untouched and bends peaks above [SOFT_KNEE] smoothly below full scale. */
    fun soften(x: Float): Float {
        val a = abs(x)
        if (a <= SOFT_KNEE) return x
        val room = 1f - SOFT_KNEE
        val bent = SOFT_KNEE + room * tanh((a - SOFT_KNEE) / room)
        return if (x < 0) -bent else bent
    }

    /** Interleaved samples with [channels] channels to mono (the average of the channels). */
    fun toMono(interleaved: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return interleaved
        val frames = interleaved.size / channels
        return ShortArray(frames) { f ->
            var sum = 0
            for (c in 0 until channels) sum += interleaved[f * channels + c]
            (sum / channels).toShort()
        }
    }

    /** Changes the sample rate by straight-line interpolation (good enough for speech and background music). */
    fun resample(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate || input.isEmpty()) return input
        val outSize = max(1, (input.size.toLong() * toRate / fromRate).toInt())
        val step = fromRate.toDouble() / toRate
        return ShortArray(outSize) { i ->
            val pos = i * step
            val a = pos.toInt().coerceAtMost(input.size - 1)
            val b = (a + 1).coerceAtMost(input.size - 1)
            val frac = (pos - a).toFloat()
            (input[a] * (1 - frac) + input[b] * frac).roundToInt().toShort()
        }
    }

    /** Loudness of [buckets] equal slices (0..1), for drawing a song's waveform. */
    fun waveform(samples: ShortArray, buckets: Int): FloatArray {
        if (samples.isEmpty() || buckets <= 0) return FloatArray(max(buckets, 0))
        val per = max(1, samples.size / buckets)
        val levels = FloatArray(buckets) { b ->
            val from = b * per
            val to = min(samples.size, from + per)
            var peak = 0
            for (i in from until to) peak = max(peak, abs(samples[i].toInt()))
            peak / 32768f
        }
        val top = levels.maxOrNull()?.takeIf { it > 0f } ?: return levels
        return FloatArray(buckets) { levels[it] / top }
    }
}
