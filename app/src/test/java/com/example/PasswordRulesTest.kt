package com.example

import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.core.PasswordRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRulesTest {
    @Test
    fun sameRuleAsSignUp() {
        assertTrue(PasswordRules.isStrongEnough("Sommer2026"))
        assertFalse(PasswordRules.isStrongEnough("Sommer"))          // too short
        assertFalse(PasswordRules.isStrongEnough("SommerSommer"))    // no number
        assertFalse(PasswordRules.isStrongEnough("  abc1234 "))      // 7 after trimming
    }

    @Test
    fun changeFormChecks() {
        assertEquals("pw_err_current_missing", PasswordRules.checkChange("", "Neues2026", "Neues2026"))
        assertEquals("pw_err_weak", PasswordRules.checkChange("Alt12345", "kurz1", "kurz1"))
        assertEquals("pw_err_same", PasswordRules.checkChange("Alt12345", "Alt12345", "Alt12345"))
        assertEquals("pw_err_mismatch", PasswordRules.checkChange("Alt12345", "Neues2026", "Neues2027"))
        assertNull(PasswordRules.checkChange("Alt12345", "Neues2026", "Neues2026"))
    }

    @Test
    fun allTextsExistInBothLanguages() {
        listOf("pw_title", "pw_update", "pw_current", "pw_forgot", "pw_new", "pw_confirm", "pw_reset_sent",
            "pw_err_current_missing", "pw_err_wrong_current", "pw_err_weak", "pw_err_same", "pw_err_mismatch",
            "pw_err_failed", "pw_done_title", "settings_account", "settings_email").forEach { key ->
            AppLanguage.entries.forEach { assertNotEquals("$it $key", key, Localization.t(key, it)) }
        }
    }
}
