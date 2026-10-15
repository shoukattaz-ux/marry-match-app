package com.example.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

sealed class LocationGateOutcome {
    /** [signupPass] is a one-time pass returned before sign-up; null for signed-in checks. */
    data class Allowed(val signupPass: String? = null) : LocationGateOutcome()
    data class NotAllowed(val message: String) : LocationGateOutcome()
    data class PermissionDenied(val message: String) : LocationGateOutcome()
    data class NetworkError(val message: String) : LocationGateOutcome()
    data class GpsUnavailable(val message: String) : LocationGateOutcome()
    data class MockRejected(val message: String) : LocationGateOutcome()
}

/**
 * Service handling Germany Location Gate verification.
 * 
 * Strict Privacy Rules:
 * - Never store or log exact GPS coordinates.
 * - Discard location coordinates immediately after the transient network check.
 */
object LocationGateService {

    const val CHECK_GERMANY_URL = "https://zaixmvhfblgarpjckjhc.supabase.co/functions/v1/check-germany"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isMockLocation(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
    }

    /**
     * Obtains a fresh single GPS location fix.
     * Uses FusedLocationProviderClient first, with LocationManager fallback.
     */
    @SuppressLint("MissingPermission")
    suspend fun getFreshGpsFix(context: Context): Location? {
        if (!hasLocationPermission(context)) return null

        return withContext(Dispatchers.IO) {
            val fusedFix = withTimeoutOrNull(10000) {
                try {
                    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                    val cts = CancellationTokenSource()
                    suspendCancellableCoroutine<Location?> { continuation ->
                        continuation.invokeOnCancellation { cts.cancel() }
                        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                            .addOnSuccessListener { loc ->
                                if (continuation.isActive) continuation.resume(loc)
                            }
                            .addOnFailureListener {
                                if (continuation.isActive) continuation.resume(null)
                            }
                    }
                } catch (e: Throwable) {
                    null
                }
            }

            if (fusedFix != null) {
                return@withContext fusedFix
            }

            // Fallback to LocationManager if FusedLocationClient returned null
            fallbackLocationManager(context)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fallbackLocationManager(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        return withTimeoutOrNull(8000) {
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            } ?: return@withTimeoutOrNull null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    try {
                        locationManager.getCurrentLocation(
                            provider,
                            signal,
                            ContextCompat.getMainExecutor(context)
                        ) { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                    } catch (e: Throwable) {
                        val last = locationManager.getLastKnownLocation(provider)
                        if (cont.isActive) cont.resume(last)
                    }
                }
            } else {
                locationManager.getLastKnownLocation(provider)
            }
        }
    }

    /**
     * Executes the check-germany request according to specification:
     * - Headers: apikey: publishable key, Content-Type: application/json
     * - If user is signed in: Authorization: Bearer <access token>
     * - Body: {"lat": ..., "lng": ..., "mock": ...}
     * - Coordinates are NEVER stored or logged.
     */
    suspend fun verifyLocationInGermany(
        context: Context,
        accessToken: String? = null
    ): LocationGateOutcome {
        if (!hasLocationPermission(context)) {
            return LocationGateOutcome.PermissionDenied("Location permission not granted")
        }

        val location = getFreshGpsFix(context)
            ?: return LocationGateOutcome.GpsUnavailable("Unable to obtain fresh GPS fix")

        val mock = isMockLocation(location)
        val lat = location.latitude
        val lng = location.longitude

        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = JSONObject().apply {
                    put("lat", lat)
                    put("lng", lng)
                    put("mock", mock)
                }.toString()

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = jsonBody.toRequestBody(mediaType)

                val requestBuilder = Request.Builder()
                    .url(CHECK_GERMANY_URL)
                    .addHeader("apikey", AppConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)

                if (!accessToken.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $accessToken")
                }

                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""
                    if (!response.isSuccessful && response.code != 200) {
                        return@withContext LocationGateOutcome.NetworkError("Server returned code ${response.code}")
                    }

                    parseGateResponse(bodyString)
                }
            } catch (e: IOException) {
                LocationGateOutcome.NetworkError("Network connection failure: ${e.message}")
            } catch (e: Throwable) {
                LocationGateOutcome.NetworkError("Unexpected error during verification: ${e.message}")
            }
        }
    }

    /** Turns the check-germany JSON response into an outcome. */
    fun parseGateResponse(body: String): LocationGateOutcome {
        val json = JSONObject(body)
        return if (json.optBoolean("allowed", false)) {
            LocationGateOutcome.Allowed(
                signupPass = json.optString("signup_pass", "").takeIf { it.isNotBlank() }
            )
        } else {
            LocationGateOutcome.NotAllowed(
                json.optString("message", "MarryMatch is not available in your area yet.")
            )
        }
    }
}
