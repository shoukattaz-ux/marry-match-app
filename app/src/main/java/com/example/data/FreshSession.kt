package com.example.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps every server call (database, storage, functions) on a valid login.
 *
 * The login token lasts one hour and is renewed in the background, but after the app was in the
 * background that renewal can finish a moment after the first call goes out: the server then
 * answers 401 and the screen shows "could not be loaded" although nothing is wrong. Here a call
 * first renews a token that is about to expire, and a call answered with 401 renews the token once
 * and is sent again. Login calls themselves (/auth/v1/) are left alone.
 */
object FreshSession {
    /** Renew this many seconds before the token runs out. */
    const val EARLY_SECONDS = 30L

    private val renewing = Mutex()

    /** Set once the Supabase client exists (the plugin is installed while it is being built). */
    @Volatile var supabase: SupabaseClient? = null

    val plugin = createClientPlugin("FreshSession") {
        on(Send) { request ->
            val supabase = this@FreshSession.supabase
            if (supabase == null || isAuthCall(request)) return@on proceed(request)
            val auth = supabase.auth
            val session = auth.currentSessionOrNull() ?: return@on proceed(request)

            if (expiresSoon(session.expiresAt.epochSeconds, nowSeconds())) {
                renew(supabase, session.accessToken)?.let { request.headers[HttpHeaders.Authorization] = "Bearer $it" }
            }
            val sentWith = request.headers[HttpHeaders.Authorization]
            val call = proceed(request)
            if (call.response.status != HttpStatusCode.Unauthorized || !canResend(request)) return@on call
            // Only calls made with a user login: a 401 for the plain app key is a real refusal.
            val sentToken = sentWith?.removePrefix("Bearer ")?.takeIf { it != sentWith } ?: return@on call
            if (sentToken != session.accessToken && sentToken != auth.currentAccessTokenOrNull()) return@on call
            val fresh = renew(supabase, sentToken) ?: return@on call
            request.headers[HttpHeaders.Authorization] = "Bearer $fresh"
            proceed(request)
        }
    }

    /** True when a token ending at [expiresAtSeconds] must be renewed before use. */
    fun expiresSoon(expiresAtSeconds: Long, nowSeconds: Long): Boolean =
        expiresAtSeconds - nowSeconds <= EARLY_SECONDS

    private fun nowSeconds() = System.currentTimeMillis() / 1000

    private fun isAuthCall(request: HttpRequestBuilder) = request.url.buildString().contains("/auth/v1/")

    /** A body that can only be read once (a file being streamed) cannot be sent a second time. */
    private fun canResend(request: HttpRequestBuilder): Boolean {
        val body = request.body
        return body !is OutgoingContent.WriteChannelContent && body !is OutgoingContent.ReadChannelContent
    }

    /**
     * One renewal at a time: calls that wait here use the token the first one got instead of
     * renewing again with a refresh token that was just used up. Null when renewing fails
     * (offline, or the login has ended); the call then goes out as it was.
     */
    private suspend fun renew(supabase: SupabaseClient, staleToken: String): String? = renewing.withLock {
        val auth = supabase.auth
        val current = auth.currentSessionOrNull() ?: return@withLock null
        if (current.accessToken != staleToken && !expiresSoon(current.expiresAt.epochSeconds, nowSeconds())) {
            return@withLock current.accessToken
        }
        try {
            auth.refreshCurrentSession()
            auth.currentAccessTokenOrNull()
        } catch (e: Throwable) {
            null
        }
    }
}
