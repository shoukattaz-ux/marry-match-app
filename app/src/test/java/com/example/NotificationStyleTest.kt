package com.example

import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.push.NotifyCategory
import com.example.push.NotifySound
import com.example.push.NotifyStyle
import com.example.push.NotifyVibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NotificationStyleTest {

    @Test
    fun everyPushKindHasACategory() {
        assertEquals(NotifyCategory.MESSAGES, NotifyCategory.forKind("message"))
        assertEquals(NotifyCategory.MESSAGES, NotifyCategory.forKind("your_turn"))
        assertEquals(NotifyCategory.MATCHES, NotifyCategory.forKind("match"))
        assertEquals(NotifyCategory.MATCHES, NotifyCategory.forKind("expiring"))
        assertEquals(NotifyCategory.LIKES, NotifyCategory.forKind("like"))
    }

    @Test
    fun everyChoiceGetsItsOwnChannel() {
        val ids = NotifyCategory.entries.flatMap { c ->
            NotifySound.entries.flatMap { s -> NotifyVibration.entries.map { v -> c to c.channelId(NotifyStyle(s, v)) } }
        }
        assertEquals(ids.size, ids.map { it.second }.toSet().size)
        assertTrue(ids.all { (c, id) -> id.startsWith(c.channelPrefix) })
        // No category's ids can be mistaken for another's when old channels are cleaned up.
        NotifyCategory.entries.forEach { a ->
            NotifyCategory.entries.filter { it != a }.forEach { b -> assertTrue(!a.channelPrefix.startsWith(b.channelPrefix)) }
        }
        assertTrue(ids.none { it.second == "marrymatch_default" })
    }

    @Test
    fun unknownSavedValuesFallBackToTheDefault() {
        assertEquals(NotifySound.BELL, NotifySound.of("trumpet", NotifySound.BELL))
        assertEquals(NotifySound.SILENT, NotifySound.of("silent", NotifySound.BELL))
        assertEquals(NotifyVibration.GENTLE, NotifyVibration.of(null, NotifyVibration.GENTLE))
    }

    @Test
    fun matchesSoundDifferentFromMessagesByDefault() {
        assertNotEquals(NotifyCategory.MESSAGES.default, NotifyCategory.MATCHES.default)
        assertEquals(null, NotifyVibration.OFF.pattern)
        assertTrue(NotifyVibration.entries.filter { it != NotifyVibration.OFF }.all { it.pattern!!.first() == 0L && it.pattern!!.size % 2 == 0 })
    }

    @Test
    fun everyOptionHasEnglishAndGermanText() {
        val keys = NotifyCategory.entries.map { "notify_cat_${it.key}" } +
            NotifyCategory.entries.map { "notify_test_body_${it.key}" } +
            NotifySound.entries.map { "notify_sound_${it.key}" } +
            NotifyVibration.entries.map { "notify_vibration_${it.key}" }
        keys.forEach { key ->
            assertNotEquals("EN $key", key, Localization.t(key, AppLanguage.EN))
            assertNotEquals("DE $key", key, Localization.t(key, AppLanguage.DE))
        }
    }

    @Test
    fun theBundledSoundsExist() {
        listOf("mm_chime", "mm_bell", "mm_melody").forEach { name ->
            val file = File("src/main/res/raw/$name.wav")
            assertTrue(name, file.isFile && file.length() in 10_000L..200_000L)
        }
    }
}
