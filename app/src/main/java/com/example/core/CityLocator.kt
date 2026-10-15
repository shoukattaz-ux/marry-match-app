package com.example.core

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class CityGuess(val city: String, val region: String)

/**
 * Suggests the user's city and Bundesland from one GPS fix. The position is turned into
 * a place name on the phone (Android Geocoder) and then discarded; only city and region
 * are ever saved.
 */
object CityLocator {
    val BUNDESLAENDER = listOf(
        "Baden-Württemberg", "Bayern", "Berlin", "Brandenburg", "Bremen", "Hamburg", "Hessen",
        "Mecklenburg-Vorpommern", "Niedersachsen", "Nordrhein-Westfalen", "Rheinland-Pfalz",
        "Saarland", "Sachsen", "Sachsen-Anhalt", "Schleswig-Holstein", "Thüringen"
    )

    /** Returns null when permission, GPS or the geocoder is unavailable, or the place is not in Germany. */
    suspend fun guess(context: Context): CityGuess? {
        if (!Geocoder.isPresent()) return null
        val location = LocationGateService.getFreshGpsFix(context) ?: return null
        return withContext(Dispatchers.IO) {
            val address = try {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.GERMANY).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
            } catch (e: Exception) {
                null
            } ?: return@withContext null
            if (address.countryCode != null && address.countryCode != "DE") return@withContext null
            val region = BUNDESLAENDER.firstOrNull { it.equals(address.adminArea, ignoreCase = true) }
                ?: return@withContext null
            val city = address.locality ?: address.subAdminArea ?: return@withContext null
            CityGuess(city = city, region = region)
        }
    }
}
