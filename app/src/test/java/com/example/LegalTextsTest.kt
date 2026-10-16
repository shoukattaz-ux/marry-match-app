package com.example

import com.example.core.AppConfig
import com.example.core.AppLanguage
import com.example.core.LegalTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalTextsTest {

    private val docs = listOf(LegalTexts.privacy, LegalTexts.terms, LegalTexts.conduct)

    @Test
    fun everyDocumentHasTheSameSectionsInBothLanguages() {
        docs.forEach { doc ->
            assertEquals(doc.titleKey, doc.sectionsEn.size, doc.sectionsDe.size)
            assertTrue(doc.sectionsEn.all { it.first.isNotBlank() && it.second.isNotBlank() })
        }
        assertEquals(LegalTexts.impressum(AppLanguage.EN).size, LegalTexts.impressum(AppLanguage.DE).size)
    }

    @Test
    fun impressumUsesTheConfiguredOperatorDetails() {
        val text = LegalTexts.impressum(AppLanguage.DE).joinToString { it.second }
        assertTrue(text.contains(AppConfig.LEGAL_OPERATOR_NAME))
        assertTrue(text.contains(AppConfig.LEGAL_ADDRESS))
        // The invented company that was in the app before must never come back.
        assertFalse(text.contains("HRB 987654"))
        assertFalse(text.contains("Friedrichstraße 123"))
    }

    @Test
    fun privacyPolicyNamesTheRealProcessing() {
        val en = LegalTexts.privacy.sectionsEn.joinToString { it.second }
        listOf("Frankfurt", "Supabase", "Art. 9", "ML Kit", "not stored").forEach {
            assertTrue("missing: $it", en.contains(it))
        }
    }
}
