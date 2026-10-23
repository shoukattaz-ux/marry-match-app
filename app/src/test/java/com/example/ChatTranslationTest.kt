package com.example

import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.core.translate.ChatLanguages
import com.example.core.translate.ChatTranslationController
import com.example.core.translate.IncomingText
import com.example.core.translate.TranslationEngine
import com.example.core.translate.TranslationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Knows German and English by a few words; "pack" downloads are counted. */
private class FakeEngine : TranslationEngine {
    var downloaded = mutableSetOf("en")
    var downloads = 0
    var translations = 0
    var offline = false
    override suspend fun identify(text: String): String? = when {
        text.length < 4 -> null
        text.contains("Hallo") || text.contains("Wie geht") -> "de"
        text.contains("Merhaba") -> "tr"
        text.contains("Hello") || text.contains("How are") -> "en"
        text.contains("Bonjour") -> "xx"  // a language the translator cannot do
        else -> null
    }
    override fun canTranslateFrom(code: String) = code != "xx"
    override suspend fun isPackReady(from: String, to: String) = from in downloaded && to in downloaded
    override suspend fun translate(text: String, from: String, to: String): String {
        if (from !in downloaded || to !in downloaded) {
            if (offline) throw IllegalStateException("no internet")
            downloads++
            downloaded += from
            downloaded += to
        }
        translations++
        return "[$from>$to] $text"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatTranslationTest {

    @Test
    fun onlyOtherLanguagesAreTranslated() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine()
        val c = ChatTranslationController(engine, this, initialTarget = "en")
        c.onMessages(listOf(IncomingText("1", "Hallo, wie war dein Tag?"), IncomingText("2", "Hello there!"), IncomingText("3", "ok")))
        assertEquals(TranslationResult.Translated("[de>en] Hallo, wie war dein Tag?", "de"), c.results.value["1"])
        assertEquals(TranslationResult.AsWritten, c.results.value["2"])  // already English
        assertEquals(TranslationResult.AsWritten, c.results.value["3"])  // too short to know
        assertEquals(1, engine.translations)
    }

    @Test
    fun offShowsEverythingAsWritten() = runTest(UnconfinedTestDispatcher()) {
        val c = ChatTranslationController(FakeEngine(), this, initialTarget = null)
        c.onMessages(listOf(IncomingText("1", "Hallo zusammen")))
        assertNull(c.results.value["1"])
    }

    @Test
    fun changingTheLanguageTranslatesAgain() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine()
        val c = ChatTranslationController(engine, this, initialTarget = "en")
        c.onMessages(listOf(IncomingText("1", "Merhaba, nasılsın?"), IncomingText("2", "Hello friend")))
        c.setTarget("de")
        assertEquals(TranslationResult.Translated("[tr>de] Merhaba, nasılsın?", "tr"), c.results.value["1"])
        assertEquals(TranslationResult.Translated("[en>de] Hello friend", "en"), c.results.value["2"])
        c.setTarget(null)
        assertEquals(emptyMap<String, TranslationResult>(), c.results.value)
    }

    @Test
    fun newMessagesAreTranslatedAsTheyArriveAndNothingTwice() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine()
        val c = ChatTranslationController(engine, this, initialTarget = "en")
        val first = listOf(IncomingText("1", "Wie geht es dir?"))
        c.onMessages(first)
        c.onMessages(first + IncomingText("2", "Hallo nochmal"))
        assertEquals("[de>en] Hallo nochmal", (c.results.value["2"] as TranslationResult.Translated).text)
        assertEquals(2, engine.translations)
        assertEquals(1, engine.downloads)  // the German pack once
        assertNull(c.downloading.value)
    }

    @Test
    fun noInternetForThePackFailsThenRetryWorks() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine().apply { offline = true }
        val c = ChatTranslationController(engine, this, initialTarget = "en")
        c.onMessages(listOf(IncomingText("1", "Hallo, schön dich zu sehen")))
        assertEquals(TranslationResult.Failed, c.results.value["1"])
        engine.offline = false
        c.retryFailed()
        assertEquals("[de>en] Hallo, schön dich zu sehen", (c.results.value["1"] as TranslationResult.Translated).text)
    }

    @Test
    fun aLanguageTheTranslatorCannotDoStaysAsWritten() = runTest(UnconfinedTestDispatcher()) {
        val c = ChatTranslationController(FakeEngine(), this, initialTarget = "en")
        c.onMessages(listOf(IncomingText("1", "Bonjour à tous")))
        assertEquals(TranslationResult.AsWritten, c.results.value["1"])
    }

    @Test
    fun languageNamesAndTexts() {
        assertEquals("Deutsch", ChatLanguages.find("de")!!.name(AppLanguage.DE))
        assertEquals("German", ChatLanguages.name("de", AppLanguage.EN))
        assertEquals(ChatLanguages.ALL.size, ChatLanguages.ALL.map { it.code }.toSet().size)
        listOf("translate_title", "translate_off", "translate_from", "translate_show_original", "translate_show_translation",
            "translate_downloading", "translate_failed", "translate_hint").forEach { key ->
            AppLanguage.entries.forEach { assertNotEquals("$it $key", key, Localization.t(key, it)) }
        }
    }
}
