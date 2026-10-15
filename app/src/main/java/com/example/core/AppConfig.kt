package com.example.core

import com.example.BuildConfig

/**
 * Global application configuration reading Supabase credentials from BuildConfig
 * (populated by the Secrets Gradle Plugin from .env or .env.example).
 */
object AppConfig {
    // Default fallback values if the Secrets panel injected a placeholder like "Secret value"
    private const val DEFAULT_PROJECT_URL = "https://zaixmvhfblgarpjckjhc.supabase.co"
    private const val DEFAULT_ANON_KEY = "sb_publishable_BPKKyNeKBkM4TISXxyXi4w_Y26xeoVi"

    val SUPABASE_URL: String = run {
        val raw = try {
            BuildConfig.SUPABASE_URL.trim()
        } catch (e: Throwable) {
            ""
        }
        if (raw.startsWith("https://") && !raw.contains(" ") && !raw.contains("your-project") && !raw.contains("Secret value")) {
            raw
        } else {
            DEFAULT_PROJECT_URL
        }
    }

    val SUPABASE_ANON_KEY: String = run {
        val raw = try {
            BuildConfig.SUPABASE_ANON_KEY.trim()
        } catch (e: Throwable) {
            ""
        }
        if (raw.isNotBlank() && !raw.contains(" ") && !raw.startsWith("PASTE") && !raw.contains("Secret value")) {
            raw
        } else {
            DEFAULT_ANON_KEY
        }
    }

    // Phone verification is disabled per business requirements
    const val PHONE_VERIFICATION_ENABLED: Boolean = false
    var phoneVerificationEnabled: Boolean = false

    const val PROD_REGION = "eu-central (Frankfurt)"
    const val MIN_AGE = 18
    const val DAILY_PROFILE_LIMIT = 10
    const val APP_VERSION = "1.0.0 (Germany Release)"

    // Versions of the texts accepted at sign-up. They are stored in the consents table,
    // so raise a version whenever its text changes.
    const val TERMS_VERSION = "1.0"
    const val PRIVACY_VERSION = "1.0"
    const val CONDUCT_VERSION = "1.0"
    const val LOCATION_NOTICE_VERSION = "1.0"
    // Consent for optional sensitive profile data (religion), GDPR Art. 9
    const val SENSITIVE_PROFILE_VERSION = "1.0"

    val isConfigured: Boolean
        get() = SUPABASE_URL.startsWith("https://") &&
                !SUPABASE_URL.contains("your-project") &&
                !SUPABASE_URL.contains(" ") &&
                SUPABASE_ANON_KEY.isNotBlank() &&
                !SUPABASE_ANON_KEY.startsWith("PASTE") &&
                !SUPABASE_ANON_KEY.contains("Secret value") &&
                !SUPABASE_ANON_KEY.contains(" ")
}
