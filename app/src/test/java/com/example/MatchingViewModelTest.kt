package com.example

import com.example.data.DailySet
import com.example.data.DiscoveryApi
import com.example.data.DiscoveryPrefs
import com.example.data.LikeResult
import com.example.data.MarryMatchRepository
import com.example.data.MatchSummary
import com.example.model.UserProfile
import com.example.ui.viewmodel.LikeTarget
import com.example.ui.viewmodel.MatchingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Server stand-in: records calls and answers like the Supabase functions would. */
private class FakeDiscoveryApi(
    var set: DailySet,
    val mutualWith: Set<String> = emptySet(),
    var failNextAction: Boolean = false
) : DiscoveryApi {
    val likes = mutableListOf<Triple<String, LikeTarget, String?>>()
    val passes = mutableListOf<String>()
    var savedPrefs: DiscoveryPrefs? = null

    override suspend fun getDailySet() = set
    override suspend fun like(toUserId: String, targetType: String?, targetId: String?, comment: String?): LikeResult {
        if (failNextAction) { failNextAction = false; throw IllegalStateException("offline") }
        likes += Triple(toUserId, LikeTarget(targetType, targetId), comment)
        return LikeResult(matched = toUserId in mutualWith, matchId = if (toUserId in mutualWith) "m-$toUserId" else null)
    }
    override suspend fun pass(toUserId: String) {
        if (failNextAction) { failNextAction = false; throw IllegalStateException("offline") }
        passes += toUserId
    }
    override suspend fun getMatches(): List<MatchSummary> = emptyList()
    override suspend fun receivedLikesCount() = 0
    override suspend fun loadPrefs() = DiscoveryPrefs()
    override suspend fun savePrefs(prefs: DiscoveryPrefs) { savedPrefs = prefs }
    override suspend fun unmatch(matchId: String) {}
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class MatchingViewModelTest {

    private lateinit var repository: MarryMatchRepository

    private val anna = UserProfile(id = "a", displayName = "Anna", age = 29, region = "Bayern", marriageTimeline = "1_2y", languages = listOf("German"), selfieVerified = true)
    private val sophie = UserProfile(id = "s", displayName = "Sophie", age = 34, region = "Berlin", marriageTimeline = "2_5y", languages = listOf("English"))
    private val lena = UserProfile(id = "l", displayName = "Lena", age = 41, region = "Bayern", marriageTimeline = "1_2y", languages = listOf("German", "Turkish"))

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = MarryMatchRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(api: FakeDiscoveryApi) = MatchingViewModel(repository, api)

    @Test
    fun loadsTodaysSetInServerOrder() {
        val vm = viewModel(FakeDiscoveryApi(DailySet(total = 10, profiles = listOf(anna, sophie, lena))))
        val state = vm.uiState.value
        assertFalse(state.loading)
        assertEquals(10, state.total)
        assertEquals("a", state.current?.id)
        assertEquals(3, state.visible.size)
    }

    @Test
    fun likeAndPassMoveToTheNextPerson() {
        val api = FakeDiscoveryApi(DailySet(3, listOf(anna, sophie, lena)))
        val vm = viewModel(api)
        vm.like()
        assertEquals("s", vm.uiState.value.current?.id)
        vm.pass()
        assertEquals("l", vm.uiState.value.current?.id)
        assertEquals(listOf("a"), api.likes.map { it.first })
        assertEquals(listOf("s"), api.passes)
    }

    @Test
    fun likeOnAPromptSendsTargetAndComment() {
        val api = FakeDiscoveryApi(DailySet(1, listOf(anna)))
        val vm = viewModel(api)
        vm.like(LikeTarget("prompt", "prompt-1", "My ideal Sunday is..."), "Same for me!")
        val (to, target, comment) = api.likes.single()
        assertEquals("a", to)
        assertEquals("prompt", target.type)
        assertEquals("prompt-1", target.id)
        assertEquals("Same for me!", comment)
        assertTrue(vm.uiState.value.doneForToday)
    }

    @Test
    fun mutualLikeShowsTheMatchCelebration() {
        val vm = viewModel(FakeDiscoveryApi(DailySet(2, listOf(anna, sophie)), mutualWith = setOf("a")))
        vm.like()
        assertEquals("a", repository.matchCelebration.value?.id)
    }

    @Test
    fun oneSidedLikeDoesNotCelebrate() {
        val vm = viewModel(FakeDiscoveryApi(DailySet(2, listOf(anna, sophie))))
        vm.like()
        assertNull(repository.matchCelebration.value)
    }

    @Test
    fun failedActionKeepsThePersonAndShowsAnError() {
        val api = FakeDiscoveryApi(DailySet(2, listOf(anna, sophie)), failNextAction = true)
        val vm = viewModel(api)
        vm.like()
        assertEquals("a", vm.uiState.value.current?.id)
        assertEquals("discover_action_failed", vm.uiState.value.errorKey)
        assertFalse(vm.uiState.value.sending)
    }

    @Test
    fun filtersHideTodaysPeopleAndAreSaved() {
        val api = FakeDiscoveryApi(DailySet(3, listOf(anna, sophie, lena)))
        val vm = viewModel(api)
        val prefs = DiscoveryPrefs(minAge = 25, maxAge = 40, region = "Bayern")
        vm.applyPrefs(prefs)
        assertEquals(listOf("a"), vm.uiState.value.visible.map { it.id })
        assertEquals(prefs, api.savedPrefs)
    }

    @Test
    fun verifiedTimelineAndLanguageFilters() {
        assertTrue(DiscoveryPrefs(verifiedOnly = true).accepts(anna))
        assertFalse(DiscoveryPrefs(verifiedOnly = true).accepts(sophie))
        assertFalse(DiscoveryPrefs(timelines = setOf("within_1y")).accepts(anna))
        assertTrue(DiscoveryPrefs(languages = setOf("Turkish")).accepts(lena))
        assertFalse(DiscoveryPrefs(languages = setOf("Turkish")).accepts(anna))
    }

    @Test
    fun blockedPersonLeavesTheSetImmediately() {
        val vm = viewModel(FakeDiscoveryApi(DailySet(3, listOf(anna, sophie, lena))))
        repository.blockUser("s")
        assertEquals(listOf("a", "l"), vm.uiState.value.visible.map { it.id })
    }

    @Test
    fun emptySetMeansDoneForToday() {
        val vm = viewModel(FakeDiscoveryApi(DailySet(0, emptyList())))
        assertTrue(vm.uiState.value.doneForToday)
    }
}
