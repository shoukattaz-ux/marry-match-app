package com.example.core.media

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteOrder

/** Decoded sound: mono 16-bit samples at [rate] samples per second. */
class MonoPcm(val samples: ShortArray, val rate: Int) {
    val durationMs: Long get() = if (rate == 0) 0 else samples.size * 1000L / rate
}

/** A song or recording that could not be read (unknown format, damaged or copy-protected). */
class AudioUnreadableException(cause: Throwable? = null) : Exception("audio_unreadable", cause)

/**
 * Reads any audio file the phone can play into mono PCM, and writes mono PCM as an AAC .m4a
 * (the voice message format), with Android's own decoders and encoders.
 */
object AudioCodec {
    private const val TIMEOUT_US = 10_000L
    private const val AAC = MediaFormat.MIMETYPE_AUDIO_AAC

    /** Decodes [startMs]..[endMs] (null: to the end, at most [maxMs]) of [uri] to mono at its own rate. */
    fun decode(context: Context, uri: Uri, startMs: Long = 0, endMs: Long? = null, maxMs: Long = 15 * 60_000L): MonoPcm {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            return decode(extractor, startMs, endMs, maxMs)
        } catch (e: AudioUnreadableException) {
            throw e
        } catch (e: Throwable) {
            throw AudioUnreadableException(e)
        } finally {
            extractor.release()
        }
    }

    fun decode(file: File, startMs: Long = 0, endMs: Long? = null, maxMs: Long = 15 * 60_000L): MonoPcm {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            return decode(extractor, startMs, endMs, maxMs)
        } catch (e: AudioUnreadableException) {
            throw e
        } catch (e: Throwable) {
            throw AudioUnreadableException(e)
        } finally {
            extractor.release()
        }
    }

    /** Length of an audio file in ms, or null when it cannot be read. */
    fun durationMs(context: Context, uri: Uri): Long? = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = audioTrack(extractor) ?: return null
            extractor.getTrackFormat(track).getLong(MediaFormat.KEY_DURATION) / 1000
        } finally {
            extractor.release()
        }
    }.getOrNull()

    private fun audioTrack(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }

    /**
     * Loudness of [buckets] equal slices of a whole song (0..1), read in one pass without
     * keeping the song in memory (a long song would need well over 100 MB as samples).
     */
    fun waveform(file: File, buckets: Int, durationMs: Long, maxMs: Long = 15 * 60_000L): FloatArray {
        val lengthUs = durationMs.coerceIn(1, maxMs) * 1000.0
        val peaks = IntArray(buckets)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            decodeInto(extractor, 0, durationMs, maxMs) { sample, tUs ->
                val b = (tUs / lengthUs * buckets).toInt().coerceIn(0, buckets - 1)
                val level = kotlin.math.abs(sample.toInt())
                if (level > peaks[b]) peaks[b] = level
            }
        } catch (e: AudioUnreadableException) {
            throw e
        } catch (e: Throwable) {
            throw AudioUnreadableException(e)
        } finally {
            extractor.release()
        }
        val top = peaks.maxOrNull()?.takeIf { it > 0 } ?: throw AudioUnreadableException()
        return FloatArray(buckets) { peaks[it].toFloat() / top }
    }

    /** Length of an audio file in ms, or null when it cannot be read. */
    fun durationMs(file: File): Long? = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track = audioTrack(extractor) ?: return null
            extractor.getTrackFormat(track).getLong(MediaFormat.KEY_DURATION) / 1000
        } finally {
            extractor.release()
        }
    }.getOrNull()

    private fun decode(extractor: MediaExtractor, startMs: Long, endMs: Long?, maxMs: Long): MonoPcm {
        val out = ShortBuilder()
        val rate = decodeInto(extractor, startMs, endMs, maxMs) { sample, _ -> out.add(sample) }
        if (out.size == 0) throw AudioUnreadableException()
        return MonoPcm(out.toArray(), rate)
    }

    /** Receives each mono sample of the chosen part with its time in µs. */
    private fun interface SampleSink {
        fun add(sample: Short, timeUs: Double)
    }

    /** Decodes [startMs]..[endMs] to mono samples for [sink]; returns the sample rate. */
    private fun decodeInto(extractor: MediaExtractor, startMs: Long, endMs: Long?, maxMs: Long, sink: SampleSink): Int {
        val track = audioTrack(extractor) ?: throw AudioUnreadableException()
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: throw AudioUnreadableException()
        val startUs = startMs * 1000
        val endUs = ((endMs ?: Long.MAX_VALUE / 1000).coerceAtMost(startMs + maxMs)) * 1000
        if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val codec = MediaCodec.createDecoderByType(mime)
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0 || extractor.sampleTime > endUs) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)!!
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.order(ByteOrder.nativeOrder())
                        // Keep only frames inside the chosen part (decoding starts at a sync point before it).
                        val frameUs = 1_000_000.0 / rate
                        var t = info.presentationTimeUs.toDouble()
                        if (floatPcm) {
                            val floats = buffer.asFloatBuffer()
                            while (floats.remaining() >= channels) {
                                var sum = 0f
                                repeat(channels) { sum += floats.get() }
                                if (t >= startUs && t < endUs) sink.add((sum / channels * 32767f).toInt().coerceIn(-32768, 32767).toShort(), t - startUs)
                                t += frameUs
                            }
                        } else {
                            val shorts = buffer.asShortBuffer()
                            while (shorts.remaining() >= channels) {
                                var sum = 0
                                repeat(channels) { sum += shorts.get() }
                                if (t >= startUs && t < endUs) sink.add((sum / channels).toShort(), t - startUs)
                                t += frameUs
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || t >= endUs) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
        return rate
    }

    /** Writes mono 16-bit [pcm] as AAC-LC in an .m4a file (replaced if it exists). */
    fun encodeM4a(pcm: MonoPcm, target: File, bitRate: Int = 64_000) {
        target.delete()
        val format = MediaFormat.createAudioFormat(AAC, pcm.rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(AAC)
        val muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxing = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var next = 0
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        buffer.clear()
                        buffer.order(ByteOrder.LITTLE_ENDIAN)
                        val count = minOf(buffer.remaining() / 2, pcm.samples.size - next)
                        val ptsUs = next * 1_000_000L / pcm.rate
                        if (count <= 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buffer.asShortBuffer().put(pcm.samples, next, count)
                            codec.queueInputBuffer(inIndex, 0, count * 2, ptsUs, 0)
                            next += count
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxing = true
                    }
                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)!!
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!isConfig && info.size > 0 && muxing) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buffer, info)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            runCatching { if (muxing) muxer.stop() }
            muxer.release()
        }
        if (!target.isFile || target.length() == 0L) throw AudioUnreadableException()
    }

    /** A growing list of samples without boxing. */
    private class ShortBuilder {
        private var data = ShortArray(64 * 1024)
        var size = 0
            private set

        fun add(s: Short) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = s
        }

        fun toArray(): ShortArray = data.copyOf(size)
    }
}
