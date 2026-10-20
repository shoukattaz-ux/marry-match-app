package com.example

import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.ChaperoneCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaperoneCodeTest {

    @Test
    fun typedCodesAreTidiedTheWayTheServerReadsThem() {
        assertEquals("RLTQH4JB", ChaperoneCode.normalize("rltq-h4jb"))
        assertEquals("RLTQH4JB", ChaperoneCode.normalize(" RLTQ H4JB "))
        assertEquals("RLTQH4JB", ChaperoneCode.normalize("RLTQH4JBXYZ"))
        assertEquals("ABCD-EF23", ChaperoneCode.format("ABCDEF23"))
        assertEquals("ABCD-E", ChaperoneCode.format("ABCDE"))
    }

    @Test
    fun theWholeSharedMessageCanBePastedInBothLanguages() {
        for (lang in AppLanguage.entries) {
            val message = String.format(Localization.t("chaperone_share_text", lang), "RLTQ-H4JB")
            assertEquals(lang.name, "RLTQH4JB", ChaperoneCode.normalize(message))
        }
    }

    @Test
    fun onlyCompleteCodesFromTheAlphabetAreSent() {
        assertTrue(ChaperoneCode.isComplete("RLTQH4JB"))
        assertFalse(ChaperoneCode.isComplete("RLTQH4J"))
        assertFalse(ChaperoneCode.isComplete("WRONG123"))  // O and 1 never appear in codes
        assertTrue(ChaperoneCode.hasLookAlike("WRONG123"))
        assertFalse(ChaperoneCode.hasLookAlike("RLTQH4JB"))
    }

    @Test
    fun theAlphabetMatchesTheServer() {
        // guardian_invite (migration 020) picks from exactly these 32 characters.
        assertEquals("ABCDEFGHJKLMNPQRSTUVWXYZ23456789", ChaperoneCode.ALPHABET)
        assertEquals(32, ChaperoneCode.ALPHABET.toSet().size)
    }
}
