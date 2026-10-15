package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MarryMatchRepository
import com.example.data.SampleDataProvider
import com.example.model.GermanCities
import com.example.model.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Filter criteria for matching and daily curation.
 */
data class MatchingFilterState(
    val minAge: Int = 22,
    val maxAge: Int = 36,
    val germanyOnly: Boolean = true,
    val selectedRegion: String = "All", // "All" or a specific Bundesland e.g. "Bayern", "Berlin"
    val selectedInterests: Set<String> = emptySet(),
    val verifiedOnly: Boolean = false,
    val mutualFamilyValues: Boolean = false
)

/**
 * Visual progress & metrics for the simulated daily curation pipeline.
 */
data class CurationProgress(
    val isCurating: Boolean = false,
    val statusMessage: String = "",
    val step: Int = 0,
    val totalEvaluated: Int = 0,
    val totalPassed: Int = 0,
    val dailyQuota: Int = 10
)

/**
 * State representing the matching view model.
 */
data class MatchingUiState(
    val filters: MatchingFilterState = MatchingFilterState(),
    val curationProgress: CurationProgress = CurationProgress(),
    val curatedProfiles: List<UserProfile> = emptyList(),
    val currentIndex: Int = 0,
    val allAvailableInterests: List<String> = listOf(
        "Alps Hiking",
        "Baking & Cooking",
        "Family Dinners",
        "Coffee Culture",
        "Reading & Literature",
        "Biking",
        "Classical Music",
        "Travel & Roadtrips",
        "Architecture & Art",
        "Nature Walks",
        "Animal Welfare"
    ),
    val lastCuratedTimestamp: String = "Today's Batch"
)

/**
 * MatchingViewModel manages profile filtering based on age, location in Germany,
 * and user interests, simulating the daily curated batch delivery process for MarryMatch.
 */
class MatchingViewModel(
    val repository: MarryMatchRepository = MarryMatchRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchingUiState())
    val uiState: StateFlow<MatchingUiState> = _uiState.asStateFlow()

    val currentProfile: StateFlow<UserProfile?> = _uiState.combine(repository.currentProfileIndex) { state, repoIdx ->
        val effectiveIdx = state.currentIndex.coerceAtLeast(repoIdx)
        if (effectiveIdx in state.curatedProfiles.indices) {
            state.curatedProfiles[effectiveIdx]
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Run initial curation on start
        refreshCuration(animated = false)
    }

    /**
     * Returns the currently active curated profile, if any.
     */
    fun getCurrentProfile(): UserProfile? {
        val state = _uiState.value
        return state.curatedProfiles.getOrNull(state.currentIndex)
    }

    /**
     * Updates target age range filters.
     */
    fun updateAgeRange(minAge: Int, maxAge: Int) {
        val clampedMin = minAge.coerceIn(18, 99)
        val clampedMax = maxAge.coerceIn(clampedMin, 99)
        _uiState.value = _uiState.value.copy(
            filters = _uiState.value.filters.copy(minAge = clampedMin, maxAge = clampedMax)
        )
        refreshCuration(animated = false)
    }

    /**
     * Toggles whether profiles must be strictly located within Germany.
     */
    fun setGermanyOnly(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            filters = _uiState.value.filters.copy(germanyOnly = enabled)
        )
        refreshCuration(animated = false)
    }

    /**
     * Sets specific German region (Bundesland) filter, or "All" for whole Germany.
     */
    fun setSelectedRegion(region: String) {
        _uiState.value = _uiState.value.copy(
            filters = _uiState.value.filters.copy(selectedRegion = region)
        )
        repository.setRegionFilter(region)
        refreshCuration(animated = false)
    }

    /**
     * Toggles an interest in the filter list.
     */
    fun toggleInterest(interest: String) {
        val currentSet = _uiState.value.filters.selectedInterests
        val newSet = if (currentSet.contains(interest)) {
            currentSet - interest
        } else {
            currentSet + interest
        }
        _uiState.value = _uiState.value.copy(
            filters = _uiState.value.filters.copy(selectedInterests = newSet)
        )
        refreshCuration(animated = false)
    }

    /**
     * Sets specific set of interests.
     */
    fun setInterests(interests: Set<String>) {
        _uiState.value = _uiState.value.copy(
            filters = _uiState.value.filters.copy(selectedInterests = interests)
        )
        refreshCuration(animated = false)
    }

    /**
     * Clears all interest filters.
     */
    fun clearInterests() {
        _uiState.value = _uiState.value.copy(
            filters = _uiState.value.filters.copy(selectedInterests = emptySet())
        )
        refreshCuration(animated = false)
    }

    /**
     * Toggles verified-only profiles.
     */
    fun setVerifiedOnly(verified: Boolean) {
        _uiState.value = _uiState.value.copy(
            filters = _uiState.value.filters.copy(verifiedOnly = verified)
        )
        repository.setVerifiedFilter(verified)
        refreshCuration(animated = false)
    }

    /**
     * Toggles mutual family values constraint (e.g. wanting children).
     */
    fun setMutualFamilyValues(required: Boolean) {
        _uiState.value = _uiState.value.copy(
            filters = _uiState.value.filters.copy(mutualFamilyValues = required)
        )
        refreshCuration(animated = false)
    }

    /**
     * Resets all filters back to default values.
     */
    fun resetFilters() {
        _uiState.value = _uiState.value.copy(
            filters = MatchingFilterState()
        )
        repository.setVerifiedFilter(false)
        repository.setRegionFilter("All")
        refreshCuration(animated = false)
    }

    /**
     * Pure business logic: Filters candidate profiles based on age, location (Germany),
     * and interests, then ranks them based on compatibility.
     */
    fun filterAndRankCandidates(
        candidates: List<UserProfile>,
        filters: MatchingFilterState,
        currentUser: UserProfile,
        blockedIds: Set<String> = emptySet()
    ): List<UserProfile> {
        val germanCityNames = GermanCities.list.map { it.name.lowercase(Locale.ROOT) }.toSet()

        return candidates
            // 1. Never show blocked users or banned accounts or self
            .filter { it.id != currentUser.id && !it.isBanned && it.id !in blockedIds }
            // 2. Location filtering: Strictly Germany
            .filter { candidate ->
                if (!filters.germanyOnly) {
                    true
                } else {
                    val isFlaggedGermany = candidate.countryOk
                    val isKnownGermanCity = candidate.city.lowercase(Locale.ROOT) in germanCityNames
                    val hasGermanRegion = candidate.region.isNotBlank() && candidate.region != "Île-de-France"
                    isFlaggedGermany && (isKnownGermanCity || hasGermanRegion)
                }
            }
            // 3. Location filtering: Specific German Bundesland / Region if selected
            .filter { candidate ->
                filters.selectedRegion == "All" || candidate.region.equals(filters.selectedRegion, ignoreCase = true)
            }
            // 4. Age filtering: within minAge..maxAge
            .filter { candidate ->
                candidate.age in filters.minAge..filters.maxAge
            }
            // 5. Verification status filter if enabled
            .filter { candidate ->
                !filters.verifiedOnly || candidate.selfieVerified
            }
            // 6. Family values filter if enabled (wants children agreement)
            .filter { candidate ->
                if (!filters.mutualFamilyValues) true
                else candidate.wantsChildren == currentUser.wantsChildren || candidate.wantsChildren == "open"
            }
            // 7. Interest filtering: if selectedInterests are specified, requires at least one match
            .filter { candidate ->
                if (filters.selectedInterests.isEmpty()) {
                    true
                } else {
                    candidate.interests.any { candidateInterest ->
                        filters.selectedInterests.any { filterInterest ->
                            candidateInterest.contains(filterInterest, ignoreCase = true) ||
                                filterInterest.contains(candidateInterest, ignoreCase = true)
                        }
                    }
                }
            }
            // 8. Daily Curation Ranking Score:
            // Combines shared interests with user, verified status, marriage timeline, and respect score
            .sortedByDescending { candidate ->
                calculateCurationScore(candidate, currentUser, filters)
            }
    }

    /**
     * Calculates compatibility score (0-100) between user and candidate.
     */
    fun calculateCurationScore(
        candidate: UserProfile,
        currentUser: UserProfile,
        filters: MatchingFilterState
    ): Int {
        var score = 50 // Base compatibility baseline

        // Overlap with user's own interests
        val sharedInterestsCount = candidate.interests.count { cInterest ->
            currentUser.interests.any { uInterest ->
                cInterest.contains(uInterest, ignoreCase = true) || uInterest.contains(cInterest, ignoreCase = true)
            }
        }
        score += (sharedInterestsCount * 10).coerceAtMost(30)

        // Bonus for matching filter interests
        if (filters.selectedInterests.isNotEmpty()) {
            val filterMatchCount = candidate.interests.count { it in filters.selectedInterests }
            score += (filterMatchCount * 5).coerceAtMost(15)
        }

        // Location proximity bonus (same region)
        if (candidate.region.equals(currentUser.region, ignoreCase = true)) {
            score += 10
        }

        // Verified profile trust bonus
        if (candidate.selfieVerified) {
            score += 8
        }
        if (candidate.phoneVerified) {
            score += 4
        }

        // Marriage timeline alignment bonus
        if (candidate.marriageTimeline == currentUser.marriageTimeline) {
            score += 8
        }

        return score.coerceIn(40, 99)
    }

    /**
     * Returns list of shared interests between current user/filters and candidate.
     */
    fun getSharedInterests(candidate: UserProfile): List<String> {
        val userInterests = repository.currentUser.value.interests
        val filterInterests = _uiState.value.filters.selectedInterests

        val allTargetInterests = (userInterests + filterInterests).toSet()
        return candidate.interests.filter { cInterest ->
            allTargetInterests.any { target ->
                cInterest.contains(target, ignoreCase = true) || target.contains(cInterest, ignoreCase = true)
            }
        }
    }

    /**
     * Executes the daily curation process.
     * When animated = true, simulates the multi-stage algorithm execution for user delight.
     */
    fun simulateDailyCuration(targetBatchSize: Int = 10, animated: Boolean = true) {
        if (!animated) {
            refreshCuration(animated = false, targetBatchSize = targetBatchSize)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                curationProgress = CurationProgress(
                    isCurating = true,
                    statusMessage = "Step 1/3: Verifying Germany location criteria & residency...",
                    step = 1,
                    dailyQuota = targetBatchSize
                )
            )
            delay(400)

            _uiState.value = _uiState.value.copy(
                curationProgress = _uiState.value.curationProgress.copy(
                    statusMessage = "Step 2/3: Applying age preferences & marriage timeline filters...",
                    step = 2
                )
            )
            delay(400)

            _uiState.value = _uiState.value.copy(
                curationProgress = _uiState.value.curationProgress.copy(
                    statusMessage = "Step 3/3: Scoring shared interests & curating today's top matches...",
                    step = 3
                )
            )
            delay(350)

            refreshCuration(animated = false, targetBatchSize = targetBatchSize)
        }
    }

    /**
     * Refreshes the curated batch based on current filters.
     */
    fun refreshCuration(animated: Boolean = false, targetBatchSize: Int = 10) {
        val allCandidates = SampleDataProvider.getDailyProfiles()
        val currentUser = repository.currentUser.value
        val blockedIds = repository.blockedUserIds.value
        val filters = _uiState.value.filters

        val rankedList = filterAndRankCandidates(allCandidates, filters, currentUser, blockedIds)
        val dailyBatch = rankedList.take(targetBatchSize)

        val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMANY)
        val formattedTime = "Curated today at ${timeFormat.format(Date())} • ${dailyBatch.size} Profiles"

        _uiState.value = _uiState.value.copy(
            curatedProfiles = dailyBatch,
            currentIndex = 0,
            curationProgress = CurationProgress(
                isCurating = false,
                statusMessage = "Curation complete! Found ${dailyBatch.size} compatible matches.",
                step = 3,
                totalEvaluated = allCandidates.size,
                totalPassed = dailyBatch.size,
                dailyQuota = targetBatchSize
            ),
            lastCuratedTimestamp = formattedTime
        )
    }

    /**
     * Likes the currently displayed curated profile.
     */
    fun likeCurrentProfile(comment: String? = null) {
        val state = _uiState.value
        val profile = state.curatedProfiles.getOrNull(state.currentIndex) ?: return
        repository.likeCurrentProfile(profile, comment)
        advanceToNextProfile()
    }

    /**
     * Passes the currently displayed curated profile.
     */
    fun passCurrentProfile() {
        val state = _uiState.value
        val profile = state.curatedProfiles.getOrNull(state.currentIndex) ?: return
        repository.passCurrentProfile()
        advanceToNextProfile()
    }

    private fun advanceToNextProfile() {
        val nextIndex = _uiState.value.currentIndex + 1
        _uiState.value = _uiState.value.copy(currentIndex = nextIndex)
    }
}
