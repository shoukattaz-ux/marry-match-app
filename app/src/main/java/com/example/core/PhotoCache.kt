package com.example.core

import coil.intercept.Interceptor
import coil.request.ImageResult

/**
 * Free-tier saver: private photos are loaded through signed links whose token changes about
 * every hour, which would make the image cache download the same photo again and again.
 * Photo files never change (a new photo gets a new name), so the cache key is the link
 * without its token: each photo is downloaded once per phone and then comes from the cache.
 */
object PhotoCache {
    private const val SIGNED_PATH = "/storage/v1/object/sign/"

    /** Stable key for a signed Supabase Storage link, or null for any other image. */
    fun keyFor(url: String): String? {
        val i = url.indexOf(SIGNED_PATH)
        if (i < 0) return null
        return "supabase-photo:" + url.substring(i + SIGNED_PATH.length).substringBefore('?')
    }

    /** Coil interceptor that applies [keyFor] to every image request. */
    val interceptor: Interceptor = object : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val request = chain.request
            val key = (request.data as? String)?.let(::keyFor)
            return if (key == null || (request.memoryCacheKey != null && request.diskCacheKey != null)) {
                chain.proceed(request)
            } else {
                chain.proceed(request.newBuilder().memoryCacheKey(key).diskCacheKey(key).build())
            }
        }
    }
}

