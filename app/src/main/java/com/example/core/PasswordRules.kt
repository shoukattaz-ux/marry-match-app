package com.example.core

/** The password rules of the app (the same at sign-up and when changing the password). */
object PasswordRules {
    const val MIN_LENGTH = 8

    /** A new password needs at least [MIN_LENGTH] characters and a number (spaces at the ends are ignored, as at login). */
    fun isStrongEnough(password: String): Boolean {
        val p = password.trim()
        return p.length >= MIN_LENGTH && p.any { it.isDigit() }
    }

    /**
     * Checks the "change password" form; returns the localization key of the first problem, or
     * null when it can be saved.
     */
    fun checkChange(current: String, new: String, confirm: String): String? = when {
        current.isBlank() -> "pw_err_current_missing"
        !isStrongEnough(new) -> "pw_err_weak"
        new.trim() == current.trim() -> "pw_err_same"
        new.trim() != confirm.trim() -> "pw_err_mismatch"
        else -> null
    }
}
