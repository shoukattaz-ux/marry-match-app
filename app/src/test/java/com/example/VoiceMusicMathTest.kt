package com.example

import com.example.core.media.VoiceMusic
import com.example.core.media.VoiceMusicMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class VoiceMusicMathTest {

    private val rate = 1000  // small rate keeps the arrays short; the maths does not depend on it

    private fun tone(n: Int, amp: Int = 10000) = ShortArray(n) { (amp * sin(it * 0.3)).toInt().toShort() }

    @Test
    fun theVoiceStaysAtFullLevelAndTheMusicIsQuieter() {
        val voice = ShortArray(4000) { 8000 }
        val music = ShortArray(4000) { 10000 }
        val out = VoiceMusicMath.mix(voice, music, rate, volume = 0.15f)
        // Middle of the message (no fade): voice + 15 % of the music.
        val expected = 8000 + 10000 * 0.15f
        assertEquals(expected, out[2000].toFloat(), 40f)
        assertEquals(voice.size, out.size)
    }

    @Test
    fun aHigherVolumeMakesTheMusicLouder() {
        val voice = ShortArray(4000)
        val music = ShortArray(4000) { 10000 }
        val quiet = VoiceMusicMath.mix(voice, music, rate, 0.10f)[2000]
        val loud = VoiceMusicMath.mix(voice, music, rate, 0.60f)[2000]
        assertEquals(1000f, quiet.toFloat(), 40f)
        assertEquals(6000f, loud.toFloat(), 60f)
    }

    @Test
    fun theMusicFadesInAndOut() {
        val voice = ShortArray(5000)
        val music = ShortArray(5000) { 10000 }
        val out = VoiceMusicMath.mix(voice, music, rate, 0.5f)
        assertEquals(0, out[0].toInt())                       // silent at the very start
        assertTrue(out[300] in 1..4999)                       // rising
        assertEquals(5000f, out[2500].toFloat(), 40f)         // full in the middle
        assertTrue(abs(out[out.size - 1].toInt()) < 50)       // silent at the very end
    }

    @Test
    fun shortMusicRepeatsToTheLengthOfTheVoice() {
        val voice = ShortArray(6000)
        val music = ShortArray(1000) { 10000 }
        val out = VoiceMusicMath.mix(voice, music, rate, 0.5f)
        assertEquals(6000, out.size)
        // Inside the third repeat (away from its joins) the music is there again.
        assertEquals(5000f, out[2500].toFloat(), 60f)
        // At a join the music dips briefly instead of clicking.
        assertTrue(abs(out[3000].toInt()) < 1000)
    }

    @Test
    fun loudPeaksAreSoftenedInsteadOfClipping() {
        val voice = ShortArray(4000) { 30000 }
        val music = ShortArray(4000) { 30000 }
        val out = VoiceMusicMath.mix(voice, music, rate, 1f)
        assertTrue(out.all { it <= 32767 && it >= -32768 })
        assertTrue(out[2000] in 29000..32767)
        assertEquals(0.5f, VoiceMusicMath.soften(0.5f), 0.0001f)  // normal levels unchanged
    }

    @Test
    fun noMusicLeavesTheVoiceUntouched() {
        val voice = tone(3000)
        assertTrue(voice.contentEquals(VoiceMusicMath.mix(voice, ShortArray(0), rate, 0.3f)))
    }

    @Test
    fun stereoBecomesMonoAndRatesConvert() {
        val stereo = shortArrayOf(100, 300, -200, 200)
        assertTrue(shortArrayOf(200, 0).contentEquals(VoiceMusicMath.toMono(stereo, 2)))
        val up = VoiceMusicMath.resample(shortArrayOf(0, 100), 16_000, 32_000)
        assertEquals(4, up.size)
        assertEquals(50, up[1].toInt())
        assertEquals(1000, VoiceMusicMath.resample(ShortArray(2000), 44_100, 22_050).size, 1)
    }

    @Test
    fun theWaveformIsScaledToItsLoudestPart() {
        val samples = ShortArray(400) { if (it < 200) 1000 else 4000 }
        val w = VoiceMusicMath.waveform(samples, 4)
        assertEquals(4, w.size)
        assertEquals(0.25f, w[0], 0.01f)
        assertEquals(1f, w[3], 0.01f)
    }

    @Test
    fun volumeChoicesStayInRange() {
        assertTrue(VoiceMusic.PRESETS.all { it in VoiceMusic.MIN_VOLUME..VoiceMusic.MAX_VOLUME })
        assertEquals(listOf(0.10f, 0.15f, 0.30f, 0.50f, 0.60f), VoiceMusic.PRESETS)
    }

    private fun assertEquals(expected: Int, actual: Int, delta: Int) = assertTrue("$actual not near $expected", abs(expected - actual) <= delta)
}
