package com.example.data.translate

import com.example.core.translate.TranslationEngine
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google ML Kit on the phone: recognises the language of a message and translates it offline.
 * Message text never leaves the phone; only the language packs (about 30 MB each) are downloaded
 * once from Google. Free and without an API key.
 */
class MlKitTranslationEngine : TranslationEngine {
    // Created on first use, so a chat shown as written loads nothing.
    // Only fairly certain guesses count: a wrong guess would "translate" a message needlessly.
    private val identifierLazy = lazy {
        LanguageIdentification.getClient(LanguageIdentificationOptions.Builder().setConfidenceThreshold(CONFIDENCE).build())
    }
    private val identifier by identifierLazy
    private val translators = HashMap<Pair<String, String>, Translator>()
    private val models by lazy { RemoteModelManager.getInstance() }

    override suspend fun identify(text: String): String? =
        identifier.identifyLanguage(text).await().takeIf { it != UNDETERMINED }?.let { LEGACY_CODES[it] ?: it }

    override fun canTranslateFrom(code: String): Boolean = TranslateLanguage.fromLanguageTag(code) != null

    override suspend fun isPackReady(from: String, to: String): Boolean =
        listOf(from, to).filter { it != TranslateLanguage.ENGLISH }.all { code ->
            models.isModelDownloaded(TranslateRemoteModel.Builder(code).build()).await()
        }

    override suspend fun translate(text: String, from: String, to: String): String {
        val translator = synchronized(translators) {
            translators.getOrPut(from to to) {
                Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.fromLanguageTag(from)!!)
                        .setTargetLanguage(TranslateLanguage.fromLanguageTag(to)!!)
                        .build()
                )
            }
        }
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        return translator.translate(text).await()
    }

    /** Frees the translation engines (call when the chat closes). */
    fun close() {
        synchronized(translators) {
            translators.values.forEach { it.close() }
            translators.clear()
        }
        if (identifierLazy.isInitialized()) identifier.close()
    }

    private companion object {
        const val UNDETERMINED = "und"
        const val CONFIDENCE = 0.6f
        // Language identification still uses older codes for these; the translator the new ones.
        val LEGACY_CODES = mapOf("iw" to "he", "fil" to "tl")
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
