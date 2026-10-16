package com.example.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val mutex = Mutex()

    /** Links for [paths], in the same order; a path that could not be signed is left out. */
    suspend fun urls(paths: List<String>): List<String> {
        if (paths.isEmpty()) return emptyList()
        val links = mutex.withLock {
            val now = nowMs()
            val missing = paths.distinct().filter { (cache[it]?.expiresAtMs ?: 0) - now < REUSE_MARGIN_MS }
            if (missing.isNotEmpty()) {
                val signed = try {
                    sign(missing, VALID_SECONDS)
                } catch (e: Throwable) {
                    // One refused path can fail the whole batch; then sign them one by one.
                    missing.mapNotNull { p -> runCatching { sign(listOf(p), VALID_SECONDS)[p] }.getOrNull()?.let { p to it } }.toMap()
                }
                signed.forEach { (path, url) -> cache[path] = Entry(url, now + VALID_SECONDS * 1000) }
            }
            paths.associateWith { cache[it]?.url }
        }
        return paths.mapNotNull { links[it] }
    }

    suspend fun url(path: String): String? = urls(listOf(path)).firstOrNull()

    /** Forget all links, e.g. at sign-out. */
    suspend fun clear() = mutex.withLock { cache.clear() }

    companion object {
        const val VALID_SECONDS = 3600L
        // Sign again when less than 10 minutes are left, so a link never expires on screen.
        private const val REUSE_MARGIN_MS = 10 * 60 * 1000L

        /** Signer for a Supabase storage bucket (batch endpoint, no image transformation). */
        fun forBucket(client: SupabaseClient, bucket: String) = PhotoUrlSigner(sign = { paths, seconds ->
            client.storage.from(bucket).createSignedUrls(seconds.seconds, paths)
                .filter { it.error == null }
                .associate { it.path to it.signedURL }
        })
    }
}
