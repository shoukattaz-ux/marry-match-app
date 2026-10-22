package com.example

import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.ui.components.StepBanners
import com.example.ui.viewmodel.OnboardingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StepBannersTest {

    @Test
    fun everySetupStepHasItsOwnCard() {
        assertEquals(OnboardingViewModel.TOTAL_STEPS, StepBanners.ALL.size)
        assertEquals(StepBanners.ALL.size, StepBanners.ALL.map { it.key }.toSet().size)
        assertEquals("purpose", StepBanners.forStep(1).key)
        assertEquals("story", StepBanners.forStep(OnboardingViewModel.TOTAL_STEPS).key)
    }

    @Test
    fun everyCardHasEnglishAndGermanText() {
        for (banner in StepBanners.ALL) for (part in listOf("tag", "title", "body")) for (lang in AppLanguage.entries) {
            val key = "banner_${banner.key}_$part"
            assertNotEquals("$lang $key", key, Localization.t(key, lang))
        }
    }
}
