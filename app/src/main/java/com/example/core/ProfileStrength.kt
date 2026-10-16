package com.example.core

import com.example.model.UserProfile

/**
 * How complete a profile is, 0..100, with the most useful next steps. Complete profiles get
 * more likes, so the profile screen shows this as a meter. Only the user sees it.
 */
data class ProfileStrength(val percent: Int, val tips: List<Tip>) {

    /** One missing part: [key] is the localization key, [points] what it adds, [opensSelfie] where tapping goes. */
    data class Tip(val key: String, val points: Int, val opensSelfie: Boolean = false)

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
        const val PROMPTS_GOOD = 3

        fun of(user: UserProfile): ProfileStrength {
            var percent = if (user.onboardingDone) 30 else 0  // name, age, region and 3 photos
            val tips = mutableListOf<Tip>()
            fun part(done: Boolean, points: Int, tip: Tip) {
                if (done) percent += points else tips += tip
            }
            part(user.selfieVerified, 15, Tip("strength_tip_selfie", 15, opensSelfie = true))
            part(user.bio.trim().length >= BIO_MIN_CHARS, 15, Tip("strength_tip_bio", 15))
            val prompts = user.prompts.count { it.answer.isNotBlank() }.coerceAtMost(PROMPTS_GOOD)
            percent += prompts * 5
            if (prompts < PROMPTS_GOOD) tips += Tip("strength_tip_prompts", (PROMPTS_GOOD - prompts) * 5)
            part(user.photos.size >= PHOTOS_GOOD, 10, Tip("strength_tip_photos", 10))
            part(user.occupation.isNotBlank() && user.education.isNotBlank(), 10, Tip("strength_tip_job", 10))
            part(user.languages.isNotEmpty() && user.heightCm > 0, 5, Tip("strength_tip_details", 5))
            return ProfileStrength(percent.coerceIn(0, 100), tips.sortedByDescending { it.points })
        }
    }
}
