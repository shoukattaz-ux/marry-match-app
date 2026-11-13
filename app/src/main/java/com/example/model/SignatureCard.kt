package com.example.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The Signature Card a selfie-verified woman can add to her profile (migration 043): what she is
 * looking for, why she is here (1-3 tags) and her vibe (emoji + short phrase), in one of four
 * designs. The server shows it only while she is verified; [id] is what a like points at.
 */
@Serializable
data class SignatureCard(
    val id: String = "",
    val template: String = SignatureCatalog.TEMPLATES.first(),
    @SerialName("looking_for") val lookingFor: String = "",
    val reasons: List<String> = emptyList(),
    @SerialName("vibe_emoji") val vibeEmoji: String? = null,
    val vibe: String? = null
)

data class CardReason(val key: String, val emoji: String, val en: String, val de: String) {
    fun label(german: Boolean) = "$emoji ${if (german) de else en}"
}

object SignatureCatalog {
    const val MAX_LOOKING_FOR = 120
    const val MAX_VIBE = 40
    const val MAX_REASONS = 3

    /** The same order as the template picker; the first is the default. */
    val TEMPLATES = listOf("midnight_glass", "champagne_pearl", "rose_velvet", "emerald_noir")

    val REASONS = listOf(
        CardReason("marriage_soon", "💍", "Marriage within a year", "Heirat innerhalb eines Jahres"),
        CardReason("shared_faith", "🤲", "Shared faith", "Gemeinsamer Glaube"),
        CardReason("family_first", "👨‍👩‍👧", "Family first", "Familie zuerst"),
        CardReason("serious_only", "🤝", "Serious intentions only", "Nur ernste Absichten"),
        CardReason("friendship_first", "🌱", "Friendship first", "Erst Freundschaft"),
        CardReason("partner_for_life", "♾️", "A partner for life", "Ein Partner fürs Leben"),
        CardReason("start_family", "👶", "Ready to start a family", "Bereit für eine Familie"),
        CardReason("shared_values", "🫶", "Respect and shared values", "Respekt und gemeinsame Werte"),
        CardReason("new_chapter", "📖", "Ready for a new chapter", "Bereit für ein neues Kapitel"),
        CardReason("home_in_germany", "🏡", "Building a home in Germany", "Ein Zuhause in Deutschland"),
        CardReason("family_blessing", "🌸", "With my family's blessing", "Mit dem Segen der Familie"),
        CardReason("honest_talk", "💬", "Honest conversations", "Ehrliche Gespräche")
    )

    /** Must match private.card_emojis() on the server. */
    val EMOJIS = listOf("☕", "🌙", "🌸", "📚", "🌿", "✨", "🎨", "🍲", "🏔️", "🌊", "🎶", "🕯️")

    fun reason(key: String) = REASONS.firstOrNull { it.key == key }

    // The same patterns as private.has_contact_info(): links, e-mail addresses, @handles,
    // messenger names and phone numbers (7+ digits, with spaces, dots, dashes or brackets between).
    private val CONTACT = Regex(
        "(https?://|www\\.|[a-z0-9._%+-]+@[a-z0-9-]+\\.[a-z]{2,}|\\b[a-z0-9-]{2,}\\.(com|de|net|org|io|me|info|eu|app)\\b|" +
            "whats ?app|telegram|insta(gram)?\\b|snap(chat)?\\b|tik ?tok|facebook|signal\\b|@[a-z0-9_.]{3,})",
        RegexOption.IGNORE_CASE
    )
    private val PHONE = Regex("([0-9][ ()./-]?){7,}")

    fun hasContactInfo(text: String?): Boolean = !text.isNullOrBlank() && (CONTACT.containsMatchIn(text) || PHONE.containsMatchIn(text))

    /** The first problem with [card] as a localization key, or null when the server will accept it. */
    fun problem(card: SignatureCard): String? {
        val looking = card.lookingFor.trim()
        val vibe = card.vibe?.trim().orEmpty()
        return when {
            card.template !in TEMPLATES -> "card_bad_template"
            looking.isEmpty() || looking.length > MAX_LOOKING_FOR -> "card_looking_for_length"
            card.reasons.distinct().size !in 1..MAX_REASONS || card.reasons.any { reason(it) == null } -> "card_bad_reasons"
            vibe.length > MAX_VIBE -> "card_vibe_length"
            card.vibeEmoji != null && card.vibeEmoji !in EMOJIS -> "card_bad_emoji"
            hasContactInfo(looking) || hasContactInfo(vibe) -> "card_contact_info"
            else -> null
        }
    }

    /** Arabic, Persian or Urdu letters: shown right-to-left in the Amiri font. */
    fun isArabicScript(text: String): Boolean = text.any { it in '؀'..'ۿ' || it in 'ݐ'..'ݿ' || it in 'ﭐ'..'﷿' || it in 'ﹰ'..'﻿' }
}
