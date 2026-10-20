package com.example.core

import com.example.model.MatchTraits
import com.example.model.PromptCatalog
import com.example.model.UserProfile

/**
 * How complete a profile is, 0..100, with the most useful next steps. Complete profiles get
 * more likes, so the profile screen shows this as a meter. Only the user sees it.
 */
data class ProfileStrength(val percent: Int, val tips: List<Tip>) {

    /**
     * One missing part: [key] is the localization key, [points] what it adds; tapping opens the
     * selfie check when [opensSelfie], otherwise the profile editor at [editorStep].
     */
    data class Tip(val key: String, val points: Int, val opensSelfie: Boolean = false, val editorStep: Int = 1)

    /** Localization key of the level label. */
    val levelKey: String
        get() = when {
            percent >= 100 -> "strength_complete"
            percent >= 80 -> "strength_strong"
            percent >= 50 -> "strength_good"
            else -> "strength_starting"
        }

    companion object {
        const val BIO_MIN_CHARS = 80
        const val PHOTOS_GOOD = 5
        const val PROMPTS_GOOD = PromptCatalog.REQUIRED
        /** All answered prompts together are worth this much. */
        const val PROMPT_POINTS = 15
        /** First of the habits / interests / values steps (OnboardingViewModel.STEP_HABITS). */
        const val MATCH_DETAILS_STEP = 7
        /** The profile questions step (OnboardingViewModel). */
        const val PROMPTS_STEP = 12

        fun of(user: UserProfile): ProfileStrength {
            var percent = if (user.onboardingDone) 20 else 0  // name, age, region and 3 photos
            val tips = mutableListOf<Tip>()
            fun part(done: Boolean, points: Int, tip: Tip) {
                if (done) percent += points else tips += tip
            }
            part(user.selfieVerified, 15, Tip("strength_tip_selfie", 15, opensSelfie = true))
            part(user.bio.trim().length >= BIO_MIN_CHARS, 15, Tip("strength_tip_bio", 15))
            val prompts = user.prompts.count { it.answer.isNotBlank() }.coerceAtMost(PROMPTS_GOOD)
            val promptPoints = prompts * PROMPT_POINTS / PROMPTS_GOOD
            percent += promptPoints
            if (prompts < PROMPTS_GOOD) tips += Tip("strength_tip_prompts", PROMPT_POINTS - promptPoints, editorStep = PROMPTS_STEP)
            part(user.photos.size >= PHOTOS_GOOD, 10, Tip("strength_tip_photos", 10))
            part(user.occupation.isNotBlank() && user.education.isNotBlank(), 10, Tip("strength_tip_job", 10))
            part(user.languages.isNotEmpty() && user.heightCm > 0, 5, Tip("strength_tip_details", 5))
            // Habits, interests and values: profiles created before these steps existed lack them.
            part(
                user.interests.size >= MatchTraits.MIN_INTERESTS && MatchTraits.ALL.all { it.key in user.traits },
                10,
                Tip("strength_tip_match", 10, editorStep = MATCH_DETAILS_STEP)
            )
            return ProfileStrength(percent.coerceIn(0, 100), tips.sortedByDescending { it.points })
        }
    }
}
