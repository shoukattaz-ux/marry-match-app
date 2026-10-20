package com.example.data

import com.example.model.UserProfile

/**
 * The member's own profile as others see it on Discover: what the privacy settings hide is
 * left out, the same way the server leaves it out of other people's cards (get_daily_set).
 */
fun UserProfile.asSeenByOthers(privacy: PrivacySettings): UserProfile = copy(
    city = if (privacy.hideCity) "" else city,
    religion = if (privacy.hideReligion) "" else religion,
    occupation = if (privacy.hideOccupation) "" else occupation,
    activity = if (privacy.hideActivity) "" else "today",
    photosBlurred = privacy.blurPhotos
)
