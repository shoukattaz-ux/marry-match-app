package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class ProfilePrompt(
    val promptKey: String,
    val questionEn: String,
    val questionDe: String,
    val answer: String
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
    val prompts: List<ProfilePrompt> = emptyList(),
    val selfieVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    val isAdmin: Boolean = false,
    val isBanned: Boolean = false,
    val countryOk: Boolean = true,
    val respectScore: Int = 100
)

val availablePromptsList = listOf(
    Pair("My ideal Sunday is...", "Mein idealer Sonntag ist..."),
    Pair("What family means to me...", "Was Familie für mich bedeutet..."),
    Pair("A value I will not compromise on...", "Ein Wert, bei dem ich keine Kompromisse eingehe..."),
    Pair("The key to a lasting marriage is...", "Der Schlüssel zu einer dauerhaften Ehe ist..."),
    Pair("How I handle disagreements...", "Wie ich Meinungsverschiedenheiten löse..."),
    Pair("My favorite tradition to share...", "Meine Lieblingstradition, die ich teilen möchte...")
)
