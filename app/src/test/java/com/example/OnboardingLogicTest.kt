package com.example

import com.example.data.MarryMatchRepository
import com.example.model.PromptCatalog
import com.example.ui.viewmodel.OnboardingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OnboardingLogicTest {

    @Test
    fun ageIsCalculatedFromExactBirthDate() {
        val birthday = LocalDate.now().minusYears(30)
        assertEquals(30, MarryMatchRepository.ageFromBirthDate(birthday.toString()))
        // One day before the 18th birthday the user is still 17.
        assertEquals(17, MarryMatchRepository.ageFromBirthDate(LocalDate.now().minusYears(18).plusDays(1).toString()))
        assertEquals(0, MarryMatchRepository.ageFromBirthDate(""))
        assertEquals(0, MarryMatchRepository.ageFromBirthDate("not-a-date"))
    }

    @Test
    fun lockedBirthDateFromSignUpIsUsed() {
        val state = OnboardingState(editMode = false, birthDate = "1995-05-05", birthDateLocked = true)
        assertEquals(LocalDate.of(1995, 5, 5), state.parseBirthDate())
    }

    @Test
    fun manualBirthDateIsValidated() {
        val valid = OnboardingState(editMode = false, birthDay = "29", birthMonth = "2", birthYear = "2000")
        assertEquals(LocalDate.of(2000, 2, 29), valid.parseBirthDate())

        val impossible = OnboardingState(editMode = false, birthDay = "30", birthMonth = "2", birthYear = "2000")
        assertNull(impossible.parseBirthDate())
        assertNull(impossible.age)
    }

    @Test
    fun onboardingStartsWithThreeDifferentEmptyPrompts() {
        val prompts = OnboardingState(editMode = false).prompts
        assertEquals(3, prompts.size)
        assertEquals(3, prompts.map { it.key }.toSet().size)
        assertTrue(prompts.all { it.answer.isEmpty() })
    }

    @Test
    fun promptKeysAreUnique() {
        val keys = PromptCatalog.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }
}
