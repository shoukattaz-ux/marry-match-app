package com.example

import com.example.core.ProfileStrength
import com.example.model.ProfilePrompt
import com.example.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileStrengthTest {

    private val prompt = ProfilePrompt("ideal_sunday", "q", "q", "Family breakfast and a long walk")

    private val minimal = UserProfile(
        id = "u", displayName = "Amira", age = 28, onboardingDone = true,
        photos = List(3) { "p$it" }, bio = "", prompts = emptyList(),
        occupation = "", education = "", languages = emptyList(), heightCm = 0, selfieVerified = false
    )

    @Test
    fun aFreshProfileStartsAtThirtyWithTheBiggestStepsFirst() {
        val s = ProfileStrength.of(minimal)
        assertEquals(30, s.percent)
        assertEquals("strength_starting", s.levelKey)
        assertEquals(listOf("strength_tip_selfie", "strength_tip_bio", "strength_tip_prompts"), s.tips.take(3).map { it.key })
        assertTrue(s.tips.first().opensSelfie)
    }

    @Test
    fun everyPartAddsUpToAHundred() {
        val full = minimal.copy(
            photos = List(5) { "p$it" },
            bio = "x".repeat(ProfileStrength.BIO_MIN_CHARS),
            prompts = List(3) { prompt },
            occupation = "Teacher", education = "Master",
            languages = listOf("German"), heightCm = 170,
            selfieVerified = true
        )
        val s = ProfileStrength.of(full)
        assertEquals(100, s.percent)
        assertEquals("strength_complete", s.levelKey)
        assertTrue(s.tips.isEmpty())
    }

    @Test
    fun promptsCountFiveEachAndBlankAnswersDoNotCount() {
        val s = ProfileStrength.of(minimal.copy(prompts = listOf(prompt, prompt.copy(answer = " "))))
        assertEquals(35, s.percent)
        assertEquals(10, s.tips.first { it.key == "strength_tip_prompts" }.points)
    }

    @Test
    fun aShortBioDoesNotCount() {
        assertEquals(30, ProfileStrength.of(minimal.copy(bio = "Hi")).percent)
    }
}
