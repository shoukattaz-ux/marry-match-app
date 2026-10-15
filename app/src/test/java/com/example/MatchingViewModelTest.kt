package com.example

import com.example.data.MarryMatchRepository
import com.example.data.SampleDataProvider
import com.example.model.UserProfile
import com.example.ui.viewmodel.MatchingFilterState
import com.example.ui.viewmodel.MatchingViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class MatchingViewModelTest {

    private lateinit var repository: MarryMatchRepository
    private lateinit var viewModel: MatchingViewModel

    @Before
    fun setup() {
        repository = MarryMatchRepository()
        viewModel = MatchingViewModel(repository)
    }

    @Test
    fun testInitialCurationPopulatesProfiles() {
        val state = viewModel.uiState.value
        assertFalse("Curated profiles should not be empty", state.curatedProfiles.isEmpty())
        assertTrue("Total evaluated candidates should be greater than 0", state.curationProgress.totalEvaluated > 0)
        assertNotNull("Current profile should not be null initially", viewModel.getCurrentProfile())
    }

    @Test
    fun testBlockedProfileLeavesTodaysSetWithoutRestartingIt() {
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val before = viewModel.uiState.value.curatedProfiles
        val first = before.first()
        viewModel.passCurrentProfile() // now looking at the second profile
        val second = viewModel.uiState.value.curatedProfiles[1]

        repository.blockUser(first.id)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val after = viewModel.uiState.value
        assertTrue(after.curatedProfiles.none { it.id == first.id })
        assertEquals("Still showing the same person", second.id, after.curatedProfiles[after.currentIndex].id)
    }

    @Test
    fun testFilterByAgeRange() {
        val candidates = SampleDataProvider.getDailyProfiles()
        val currentUser = repository.currentUser.value

        // Filter for age between 25 and 28
        val filters = MatchingFilterState(
            minAge = 25,
            maxAge = 28,
            germanyOnly = false
        )
        val filtered = viewModel.filterAndRankCandidates(candidates, filters, currentUser)

        assertFalse("Filtered list should not be empty", filtered.isEmpty())
        assertTrue("All candidates must be between 25 and 28", filtered.all { it.age in 25..28 })
    }

    @Test
    fun testFilterByLocationStrictlyGermany() {
        val candidates = SampleDataProvider.getDailyProfiles()
        val currentUser = repository.currentUser.value

        // Strictly Germany filter
        val filtersGermany = MatchingFilterState(
            minAge = 18,
            maxAge = 50,
            germanyOnly = true
        )
        val filteredGermany = viewModel.filterAndRankCandidates(candidates, filtersGermany, currentUser)

        // Make sure foreign candidate (e.g. c7_foreign with countryOk = false in Paris) is excluded
        assertTrue("All candidates must be within Germany", filteredGermany.all { it.countryOk })
        assertFalse("Foreign candidate should be filtered out", filteredGermany.any { it.id == "c7_foreign" })

        // When Germany only is false, foreign candidates are allowed
        val filtersWorldwide = MatchingFilterState(
            minAge = 18,
            maxAge = 50,
            germanyOnly = false
        )
        val filteredWorldwide = viewModel.filterAndRankCandidates(candidates, filtersWorldwide, currentUser)
        assertTrue("Foreign candidate allowed when germanyOnly is false", filteredWorldwide.any { it.id == "c7_foreign" })
    }

    @Test
    fun testFilterBySpecificGermanRegion() {
        val candidates = SampleDataProvider.getDailyProfiles()
        val currentUser = repository.currentUser.value

        // Filter strictly for Bayern
        val bayernFilters = MatchingFilterState(
            minAge = 18,
            maxAge = 50,
            germanyOnly = true,
            selectedRegion = "Bayern"
        )
        val bayernProfiles = viewModel.filterAndRankCandidates(candidates, bayernFilters, currentUser)

        assertFalse("Bayern profiles should be found", bayernProfiles.isEmpty())
        assertTrue("All filtered profiles must be from Bayern", bayernProfiles.all { it.region.equals("Bayern", ignoreCase = true) })
    }

    @Test
    fun testFilterByInterests() {
        val candidates = SampleDataProvider.getDailyProfiles()
        val currentUser = repository.currentUser.value

        // Filter by Classical Music interest
        val interestFilters = MatchingFilterState(
            minAge = 18,
            maxAge = 50,
            selectedInterests = setOf("Classical Music")
        )
        val musicProfiles = viewModel.filterAndRankCandidates(candidates, interestFilters, currentUser)

        assertFalse("Profiles matching Classical Music should be found", musicProfiles.isEmpty())
        assertTrue("Every profile must contain Classical Music", musicProfiles.all { candidate ->
            candidate.interests.any { it.contains("Classical Music", ignoreCase = true) }
        })
    }

    @Test
    fun testDailyCurationRankingPrioritizesSharedInterests() {
        val candidates = SampleDataProvider.getDailyProfiles()
        val currentUser = repository.currentUser.value.copy(
            interests = listOf("Alps Hiking", "Baking & Cooking")
        )

        val filters = MatchingFilterState(
            minAge = 18,
            maxAge = 50,
            germanyOnly = true
        )
        val ranked = viewModel.filterAndRankCandidates(candidates, filters, currentUser)

        assertFalse(ranked.isEmpty())
        val topProfile = ranked.first()

        // Top profile should have a high curation score
        val topScore = viewModel.calculateCurationScore(topProfile, currentUser, filters)
        assertTrue("Top score should be high (>= 70)", topScore >= 70)
    }

    @Test
    fun testLikeAndPassAdvanceProfile() {
        val initialIdx = viewModel.uiState.value.currentIndex
        viewModel.passCurrentProfile()
        assertEquals("Index should advance after pass", initialIdx + 1, viewModel.uiState.value.currentIndex)

        viewModel.likeCurrentProfile("Great profile!")
        assertEquals("Index should advance after like", initialIdx + 2, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun testSimulateDailyCurationSync() {
        viewModel.simulateDailyCuration(targetBatchSize = 5, animated = false)
        val state = viewModel.uiState.value
        assertEquals("Current index resets on new curation", 0, state.currentIndex)
        assertTrue("Curated profiles batch count should be at most 5", state.curatedProfiles.size <= 5)
        assertEquals("Curation step should be complete (step 3)", 3, state.curationProgress.step)
        assertFalse("isCurating should be false when completed", state.curationProgress.isCurating)
    }
}
