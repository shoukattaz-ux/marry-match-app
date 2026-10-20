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

    @Test
    fun aManOnlySeesWomanAndAWomanOnlyMan() {
        assertEquals(listOf("woman"), com.example.ui.viewmodel.OnboardingViewModel.lookingForOptions("man"))
        assertEquals(listOf("man"), com.example.ui.viewmodel.OnboardingViewModel.lookingForOptions("woman"))
        assertTrue(com.example.ui.viewmodel.OnboardingViewModel.lookingForOptions("").isEmpty())
    }

    @Test
    fun choosingAGenderPicksTheMatchingLookingFor() {
        val vm = com.example.ui.viewmodel.OnboardingViewModel
        assertEquals("woman", vm.lookingForAfterGender("man"))
        assertEquals("man", vm.lookingForAfterGender("woman"))
        assertEquals("", vm.lookingForAfterGender("other"))
        assertEquals("", vm.knownGender("other"))
        assertEquals(listOf("man", "woman"), vm.GENDERS)
        assertEquals("", vm.lookingForAfterGender(""))
    }

    @Test
    fun habitsAndValuesStepsNeedEveryAnswer() {
        val vm = com.example.ui.viewmodel.OnboardingViewModel
        val habits = com.example.model.MatchTraits.HABITS
        val all = habits.associate { it.key to it.options.first() }
        val state = com.example.ui.viewmodel.OnboardingState(editMode = false)
        assertTrue(vm.missingAnswers(state, habits))
        assertTrue(vm.missingAnswers(state.copy(traits = all - "sleep"), habits))
        assertTrue(vm.missingAnswers(state.copy(traits = all + ("diet" to "halal")), habits))
        assertTrue(!vm.missingAnswers(state.copy(traits = all), habits))
        assertEquals(13, vm.TOTAL_STEPS)
        assertEquals(listOf(7, 8, 9), listOf(vm.STEP_HABITS, vm.STEP_INTERESTS, vm.STEP_VALUES))
        assertEquals(com.example.core.ProfileStrength.MATCH_DETAILS_STEP, vm.STEP_HABITS)
    }

    @Test
    fun photoPickerOffersOnlyTheFreePlaces() {
        val vm = com.example.ui.viewmodel.OnboardingViewModel
        assertEquals(6, vm.photosStillAllowed(0))
        assertEquals(3, vm.photosStillAllowed(3))
        assertEquals(0, vm.photosStillAllowed(6))
        assertEquals(0, vm.photosStillAllowed(7))
    }
}
