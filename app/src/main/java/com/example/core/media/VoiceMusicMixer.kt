package com.example.core.media

import java.io.File

/**
 * Puts background music under a recorded voice message: decodes both, brings them to
 * [VoiceMusicMath.MIX_RATE], mixes (voice 100 %, music at its volume, looped or cut, faded) and
 * writes an AAC .m4a. Runs for about a second per minute of voice; call it off the main thread.
 */
object VoiceMusicMixer {
    /** 64 kbps mono: clear music at about 0.5 MB per minute. */
    const val BIT_RATE = 64_000

    fun mix(voiceFile: File, music: VoiceMusic, target: File) {
        val voice = AudioCodec.decode(voiceFile)
        val voicePcm = VoiceMusicMath.resample(voice.samples, voice.rate, VoiceMusicMath.MIX_RATE)
        val song = File(music.songFile)
        if (!song.isFile) throw AudioUnreadableException()
        // Only as much of the song as the voice needs (a short part repeats instead).
        val needMs = minOf(music.lengthMs, voice.durationMs + 250)
        val songPart = AudioCodec.decode(song, music.startMs, music.startMs + needMs)
        val musicPcm = VoiceMusicMath.resample(songPart.samples, songPart.rate, VoiceMusicMath.MIX_RATE)
        val mixed = VoiceMusicMath.mix(voicePcm, musicPcm, VoiceMusicMath.MIX_RATE, music.volume)
        AudioCodec.encodeM4a(MonoPcm(mixed, VoiceMusicMath.MIX_RATE), target, BIT_RATE)
    }
}
