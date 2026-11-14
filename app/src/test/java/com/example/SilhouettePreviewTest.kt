package com.example

import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.PrivacySettings
import com.example.data.asSeenByOthers
import com.example.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The silhouette profile (migration 049) as the member's own preview shows it. */
class SilhouettePreviewTest {
    private val photos = listOf("a.jpg", "b.jpg", "c.jpg")
    private val woman = UserProfile(id = "w", gender = "woman", selfieVerified = true, photos = photos, photoIds = listOf("1", "2", "3"))

    @Test
    fun aVerifiedWomanShowsASilhouetteWithoutPhotos() {
        val seen = woman.asSeenByOthers(PrivacySettings(silhouette = true))
        assertTrue(seen.silhouette)
        assertTrue(seen.photos.isEmpty())
        assertTrue(seen.photoIds.isEmpty())
    }

    @Test
    fun offByDefaultAndPhotosStay() {
        val seen = woman.asSeenByOthers(PrivacySettings())
        assertFalse(seen.silhouette)
        assertEquals(photos, seen.photos)
    }

    @Test
    fun onlyForVerifiedWomen() {
        val unverified = woman.copy(selfieVerified = false).asSeenByOthers(PrivacySettings(silhouette = true))
        assertFalse(unverified.silhouette)
        assertEquals(photos, unverified.photos)
        val man = woman.copy(gender = "man").asSeenByOthers(PrivacySettings(silhouette = true))
        assertFalse(man.silhouette)
        assertEquals(photos, man.photos)
    }

    @Test
    fun textsInBothLanguages() {
        listOf("privacy_silhouette", "privacy_silhouette_desc", "privacy_silhouette_locked", "silhouette_badge").forEach {
            assertNotEquals(it, Localization.t(it, AppLanguage.EN))
            assertNotEquals(it, Localization.t(it, AppLanguage.DE))
            assertNotEquals(Localization.t(it, AppLanguage.EN), Localization.t(it, AppLanguage.DE))
        }
    }
}
