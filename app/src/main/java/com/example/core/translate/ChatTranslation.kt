package com.example.core.translate

import com.example.core.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A language incoming chat messages can be shown in (ML Kit language codes). */
data class ChatLanguage(val code: String, val nameEn: String, val nameDe: String, val nativeName: String) {
    fun name(lang: AppLanguage): String = if (lang == AppLanguage.DE) nameDe else nameEn
}

object ChatLanguages {
    /** Languages members in Germany use most; ML Kit translates between all of them on the phone. */
    val ALL = listOf(
        ChatLanguage("en", "English", "Englisch", "English"),
        ChatLanguage("de", "German", "Deutsch", "Deutsch"),
        ChatLanguage("tr", "Turkish", "Türkisch", "Türkçe"),
        ChatLanguage("ar", "Arabic", "Arabisch", "العربية"),
        ChatLanguage("ur", "Urdu", "Urdu", "اردو"),
        ChatLanguage("hi", "Hindi", "Hindi", "हिन्दी"),
        ChatLanguage("fa", "Persian", "Persisch", "فارسی"),
        ChatLanguage("bn", "Bengali", "Bengalisch", "বাংলা"),
        ChatLanguage("ru", "Russian", "Russisch", "Русский"),
        ChatLanguage("uk", "Ukrainian", "Ukrainisch", "Українська"),
        ChatLanguage("pl", "Polish", "Polnisch", "Polski"),
        ChatLanguage("ro", "Romanian", "Rumänisch", "Română"),
        ChatLanguage("fr", "French", "Französisch", "Français"),
        ChatLanguage("es", "Spanish", "Spanisch", "Español"),
        ChatLanguage("it", "Italian", "Italienisch", "Italiano")
    )

    fun find(code: String?): ChatLanguage? = ALL.firstOrNull { it.code == code }

    /** Name of any detected language for "Translated from …" (the code itself when unknown). */
    fun name(code: String, lang: AppLanguage): String =
        find(code)?.name(lang) ?: java.util.Locale.forLanguageTag(code)
            .getDisplayLanguage(if (lang == AppLanguage.DE) java.util.Locale.GERMAN else java.util.Locale.ENGLISH)
            .ifBlank { code }
}

/** What happened to one incoming message for the chosen language. */
sealed interface TranslationResult {
    data object Working : TranslationResult
    /** Already in the chosen language, or the language could not be recognised: shown as written. */
    data object AsWritten : TranslationResult
    data class Translated(val text: String, val fromCode: String) : TranslationResult
    /** The language pack could not be downloaded or the translation failed (tap to try again). */
    data object Failed : TranslationResult
}

/** The on-device translator (ML Kit on the phone, a fake in tests). */
interface TranslationEngine {
    /** The language of [text], or null when it is not clear (short or mixed messages). */
    suspend fun identify(text: String): String?
    fun canTranslateFrom(code: String): Boolean
    suspend fun isPackReady(from: String, to: String): Boolean
    /** Translates, downloading the language packs first when needed. */
    suspend fun translate(text: String, from: String, to: String): String
}

/** An incoming text message to show in the chosen language. */
data class IncomingText(val id: String, val text: String)

/**
 * Shows the other person's messages in the language this user chose for the chat. A message
 * already in that language, or one whose language is unclear, stays as written; the user's own
 * messages are never translated. Work runs one message at a time, newest wishes first.
 */
class ChatTranslationController(
    private val engine: TranslationEngine,
    private val scope: CoroutineScope,
    initialTarget: String?
) {
    private val _target = MutableStateFlow(initialTarget)
    /** Chosen language code, or null for "show messages as written". */
    val target: StateFlow<String?> = _target.asStateFlow()

    private val _results = MutableStateFlow<Map<String, TranslationResult>>(emptyMap())
    val results: StateFlow<Map<String, TranslationResult>> = _results.asStateFlow()

    private val _downloading = MutableStateFlow<String?>(null)
    /** Code of the language pack being downloaded right now (shown as a one-time notice). */
    val downloading: StateFlow<String?> = _downloading.asStateFlow()

    private val oneAtATime = Mutex()
    private var known: List<IncomingText> = emptyList()
    // The same text in the same pair is translated only once.
    private val memo = HashMap<Triple<String, String, String>, String>()

    fun setTarget(code: String?) {
        if (code == _target.value) return
        _target.value = code
        _results.value = emptyMap()
        process(known)
    }

    /** Call with the other person's text messages whenever the chat changes. */
    fun onMessages(messages: List<IncomingText>) {
        known = messages
        process(messages)
    }

    /** Tries the messages again that could not be translated (e.g. no internet for the download). */
    fun retryFailed() {
        _results.update { it.filterValues { r -> r != TranslationResult.Failed } }
        process(known)
    }

    private fun process(messages: List<IncomingText>) {
        val to = _target.value ?: return
        val todo = messages.filter { it.id !in _results.value && it.text.isNotBlank() }
        if (todo.isEmpty()) return
        _results.update { r -> r + todo.associate { it.id to TranslationResult.Working } }
        scope.launch {
            oneAtATime.withLock {
                // Newest first: those are on screen.
                for (m in todo.asReversed()) {
                    if (_target.value != to) return@withLock
                    val result = translateOne(m.text, to)
                    if (_target.value == to) _results.update { it + (m.id to result) }
                }
            }
        }
    }

    private suspend fun translateOne(text: String, to: String): TranslationResult = try {
        val from = engine.identify(text)
        when {
            from == null || from == to || !engine.canTranslateFrom(from) -> TranslationResult.AsWritten
            else -> {
                val key = Triple(text, from, to)
                val translated = memo[key] ?: run {
                    val needsDownload = !engine.isPackReady(from, to)
                    if (needsDownload) _downloading.value = from
                    try {
                        engine.translate(text, from, to)
                    } finally {
                        if (needsDownload) _downloading.value = null
                    }
                }.also { memo[key] = it }
                TranslationResult.Translated(translated, from)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        TranslationResult.Failed
    }
}
