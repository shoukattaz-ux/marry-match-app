package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class ProfilePrompt(
    val promptKey: String,
    val questionEn: String,
    val questionDe: String,
    val answer: String,
    val id: String = ""  // database id, used to like this exact prompt
)

@Serializable
data class UserProfile(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val birthDate: String = "", // YYYY-MM-DD exact date
    val birthYear: Int = 1998,
    val age: Int = 0,
    val gender: String = "man", // 'woman' | 'man' | 'other'
    val lookingFor: String = "woman", // 'woman' | 'man' | 'any'
    val city: String = "",
    val region: String = "", // Bundesland
    val bio: String = "",
    val marriageTimeline: String = "1_2y", // 'within_1y' | '1_2y' | '2_5y' | 'open'
    val wantsChildren: String = "yes", // 'yes' | 'no' | 'open'
    val religion: String = "", // sensitive optional data
    val smoking: String = "Non-smoker",
    val education: String = "",
    val occupation: String = "",
    val languages: List<String> = listOf("German", "English"),
    val interests: List<String> = listOf("Hiking", "Cooking", "Family Dinners", "Reading", "Travel"),
    val heightCm: Int = 175,
    val onboardingDone: Boolean = false,
    val photos: List<String> = emptyList(), // image urls
    val photoIds: List<String> = emptyList(), // database ids of [photos], same order
    val prompts: List<ProfilePrompt> = emptyList(),
    val selfieVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    val isAdmin: Boolean = false,
    val isBanned: Boolean = false,
    val countryOk: Boolean = true,
    val respectScore: Int = 100,
    /** "today", "week" or "" (unknown or hidden); never an exact time. */
    val activity: String = "",
    /** [photos] are blurred copies: this member shows clear photos only to matches. */
    val photosBlurred: Boolean = false,
    /** A chaperone (family member) reads this member's chats with matches. */
    val chaperoned: Boolean = false,
    /** How the round avatar shows the main photo (set with "Adjust photo"). */
    val mainPhotoFocus: PhotoFocus = PhotoFocus()
)

/** A question users can answer on their profile; [key] is what the database stores. */
data class PromptQuestion(val key: String, val questionEn: String, val questionDe: String)

object PromptCatalog {
    val all = listOf(
        PromptQuestion("ideal_sunday", "My ideal Sunday is...", "Mein idealer Sonntag ist..."),
        PromptQuestion("family_means", "What family means to me...", "Was Familie für mich bedeutet..."),
        PromptQuestion("non_negotiable", "A value I will not compromise on...", "Ein Wert, bei dem ich keine Kompromisse eingehe..."),
        PromptQuestion("lasting_marriage", "The key to a lasting marriage is...", "Der Schlüssel zu einer dauerhaften Ehe ist..."),
        PromptQuestion("disagreements", "How I handle disagreements...", "Wie ich Meinungsverschiedenheiten löse..."),
        PromptQuestion("favorite_tradition", "My favorite tradition to share...", "Meine Lieblingstradition, die ich teilen möchte...")
    )

    fun find(key: String): PromptQuestion? = all.firstOrNull { it.key == key }
}
