package com.example.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlin.time.Duration.Companion.seconds

/**
 * Profile photos are private (migration 012): the app shows them through short-lived signed
 * links, which the server only creates for people allowed to see the photo. Links are reused
 * while they are valid, so the same photo keeps the same link and stays in the image cache.
 *
 * [sign] signs several paths in one request and returns path to link; paths the server refuses
 * are left out. It is a parameter so the caching can be tested without a server.
 */
class PhotoUrlSigner(
    private val sign: suspend (List<String>, Long) -> Map<String, String>,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private class Entry(val url: String, val expiresAtMs: Long)

    private val cache = HashMap<String, Entry>()
    // Paths the server would not sign (e.g. an older photo without a small copy): not asked again for a while.
    private val refusedUntil = HashMap<String, Long>()
    private val mutex = Mutex()

    /** Links for [paths], in the same order; a path that could not be signed is left out. */
    suspend fun urls(paths: List<String>): List<String> {
        if (paths.isEmpty()) return emptyList()
        val links = mutex.withLock {
            val now = nowMs()
            val missing = paths.distinct().filter {
                (cache[it]?.expiresAtMs ?: 0) - now < REUSE_MARGIN_MS && (refusedUntil[it] ?: 0) <= now
            }
            if (missing.isNotEmpty()) {
                // A path the server answered for but did not sign is refused; a failed request
                // (network) is not, so the photo is tried again on the next screen.
                val answered = mutableSetOf<String>()
                val signed = try {
                    sign(missing, VALID_SECONDS).also { answered += missing }
                } catch (e: Throwable) {
                    // One refused path can fail the whole batch; then sign them one by one.
                    missing.mapNotNull { p ->
                        runCatching { sign(listOf(p), VALID_SECONDS) }.getOrNull()
                            ?.also { answered += p }
                            ?.get(p)?.let { p to it }
                    }.toMap()
                }
                signed.forEach { (path, url) -> cache[path] = Entry(url, now + VALID_SECONDS * 1000) }
                answered.filter { it !in signed }.forEach { refusedUntil[it] = now + RETRY_REFUSED_MS }
            }
            paths.associateWith { cache[it]?.url }
        }
        return paths.mapNotNull { links[it] }
    }

    suspend fun url(path: String): String? = urls(listOf(path)).firstOrNull()

    /** Forget all links, e.g. at sign-out. */
    suspend fun clear() = mutex.withLock {
        cache.clear()
        refusedUntil.clear()
    }

    companion object {
        const val VALID_SECONDS = 3600L
        // Sign again when less than 10 minutes are left, so a link never expires on screen.
        private const val REUSE_MARGIN_MS = 10 * 60 * 1000L
        private const val RETRY_REFUSED_MS = 10 * 60 * 1000L

        /** Signer for a Supabase storage bucket (batch endpoint, no image transformation). */
        fun forBucket(client: SupabaseClient, bucket: String) = PhotoUrlSigner(sign = { paths, seconds ->
            try {
                client.storage.from(bucket).createSignedUrls(seconds.seconds, paths)
                    .filter { it.error == null }
                    .associate { it.path to it.signedURL }
            } catch (e: SerializationException) {
                // A missing file comes back without a link, which the library cannot read. For a
                // single path that means "refused"; for a batch, the caller retries one by one.
                if (paths.size == 1) emptyMap() else throw e
            }
        })
    }
}
