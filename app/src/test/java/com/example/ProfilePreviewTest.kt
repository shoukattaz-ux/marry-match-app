package com.example

import com.example.data.PrivacySettings
import com.example.data.asSeenByOthers
import com.example.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePreviewTest {

    private val me = UserProfile(displayName = "Amira", city = "München", religion = "Islam", occupation = "Teacher")

    @Test
    fun hiddenDetailsAreLeftOutLikeOnOtherPeoplesCards() {
        val seen = me.asSeenByOthers(PrivacySettings(hideCity = true, hideReligion = true, hideOccupation = true, hideActivity = true, blurPhotos = true))
        assertEquals("", seen.city)
        assertEquals("", seen.religion)
        assertEquals("", seen.occupation)
        assertEquals("", seen.activity)
        assertTrue(seen.photosBlurred)
    }

    @Test
    fun withDefaultSettingsEverythingIsShown() {
        val seen = me.asSeenByOthers(PrivacySettings())
        assertEquals("München", seen.city)
        assertEquals("Teacher", seen.occupation)
        assertEquals("today", seen.activity)
    }
}
