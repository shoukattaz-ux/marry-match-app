package com.example.core

/**
 * Throwaway email domains refused at sign-up. The server enforces the same list
 * (table blocked_email_domains, migration 004); this copy only gives a friendly message first.
 */
object DisposableEmail {
    private val domains = setOf(
        "mailinator.com", "guerrillamail.com", "guerrillamail.de", "sharklasers.com",
        "10minutemail.com", "temp-mail.org", "tempmail.com", "tempail.com",
        "yopmail.com", "trashmail.com", "trash-mail.com", "wegwerfmail.de",
        "einrot.com", "byom.de", "getnada.com", "dispostable.com", "maildrop.cc",
        "throwawaymail.com", "fakeinbox.com", "mailnesia.com", "emailondeck.com",
        "mohmal.com", "burnermail.io", "spamgourmet.com"
    )

    fun isDisposable(email: String): Boolean =
        email.substringAfterLast('@', "").trim().lowercase() in domains
}
