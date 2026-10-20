package com.example.model

/**
 * One single-choice profile question; [key] is the profiles column, [labelKey] the localized
 * question and "${key}_$option" each localized answer. The server checks the same options
 * (migration 029) and uses them to rank matches.
 */
data class Trait(val key: String, val options: List<String>, val labelKey: String = "trait_$key") {
    fun optionKey(option: String) = "${key}_$option"
}

object MatchTraits {
    /** Onboarding "Habits" step. */
    val HABITS = listOf(
        Trait("smoking", listOf("non_smoker", "occasionally", "smoker"), labelKey = "smoking"),
        Trait("drinking", listOf("never", "socially", "regularly")),
        Trait("exercise", listOf("rarely", "sometimes", "often", "daily")),
        Trait("diet", listOf("anything", "vegetarian", "vegan", "pescatarian")),
        Trait("pets", listOf("have_pets", "like_pets", "no_pets", "allergic")),
        Trait("sleep", listOf("early_bird", "night_owl", "flexible"))
    )

    /** Onboarding "Values & personality" step. */
    val VALUES = listOf(
        Trait("personality", listOf("introvert", "ambivert", "extrovert")),
        Trait("family_closeness", listOf("very_close", "close", "independent")),
        Trait("household", listOf("traditional", "shared", "flexible")),
        Trait("home_after_marriage", listOf("city", "countryside", "flexible")),
        Trait("relocate", listOf("yes", "maybe", "no"))
    )

    val ALL = HABITS + VALUES

    /** Onboarding "Interests & hobbies" step; the label of each is "interest_$key". */
    val INTERESTS = listOf(
        "cooking", "baking", "travel", "hiking", "nature", "cycling", "running", "gym",
        "football", "swimming", "yoga", "dancing", "music", "concerts", "reading", "writing",
        "art", "photography", "movies", "series", "gaming", "tech", "cars", "gardening",
        "volunteering", "family_time", "board_games", "coffee", "fashion", "learning_languages",
        "animals", "theatre", "museums"
    )
    const val MIN_INTERESTS = 3
    const val MAX_INTERESTS = 10

    fun find(key: String): Trait? = ALL.firstOrNull { it.key == key }

    /** Keeps only answers that are still offered, so an old value never breaks the step. */
    fun known(values: Map<String, String?>): Map<String, String> =
        ALL.mapNotNull { trait -> values[trait.key]?.takeIf { it in trait.options }?.let { trait.key to it } }.toMap()

    /** Interests both people share, in catalog order. */
    fun shared(mine: List<String>, theirs: List<String>): List<String> =
        INTERESTS.filter { it in mine && it in theirs }
}
