package com.example

import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.DailySet
import com.example.data.DiscoveryApi
import com.example.data.DiscoveryPrefs
import com.example.data.LikeResult
import com.example.data.MatchSummary
import com.example.data.ReceivedLike
import com.example.model.PromptCatalog
import com.example.model.UserProfile
import com.example.ui.viewmodel.LikesYouText
import com.example.ui.viewmodel.LikesYouViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Behaves like get_received_likes / like_back of migration 045, in memory. */
private class FakeLikesApi : DiscoveryApi {
    val likes = mutableListOf<ReceivedLike>()
    val likedBack = mutableListOf<Pair<String, String?>>()
    val passed = mutableListOf<String>()
    var failNext: String? = null
    var loads = 0

    override suspend fun receivedLikes(): List<ReceivedLike> { loads++; return likes.toList() }
    override suspend fun likeBack(fromUserId: String, comment: String?): LikeResult {
        failNext?.let { failNext = null; throw RuntimeException(it) }
        likedBack += fromUserId to comment
        likes.removeAll { it.profile.id == fromUserId }
        return LikeResult(matched = true, matchId = "m-$fromUserId")
    }
    override suspend fun pass(toUserId: String) {
        failNext?.let { failNext = null; throw RuntimeException(it) }
        passed += toUserId
        likes.removeAll { it.profile.id == toUserId }
    }
    override suspend fun getDailySet() = DailySet(0, emptyList())
    override suspend fun like(toUserId: String, targetType: String?, targetId: String?, comment: String?) = LikeResult(false, null)
    override suspend fun getMatches(): List<MatchSummary> = emptyList()
    override suspend fun receivedLikesCount() = likes.size
    override suspend fun loadPrefs() = DiscoveryPrefs()
    override suspend fun savePrefs(prefs: DiscoveryPrefs) {}
    override suspend fun unmatch(matchId: String) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class LikesYouViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun like(id: String, type: String? = null, comment: String? = null, promptKey: String? = null, new: Boolean = false) =
        ReceivedLike(UserProfile(id = id, displayName = id.replaceFirstChar { it.uppercase() }, age = 30),
            targetType = type, comment = comment, promptKey = promptKey, isNew = new)

    private fun api() = FakeLikesApi().apply {
        likes += like("omar", "photo", "Lovely smile", new = true)
        likes += like("max", "prompt", promptKey = PromptCatalog.all.first().key)
    }

    @Test
    fun showsEveryoneWhoLikedMe() {
        val vm = LikesYouViewModel(api())
        assertFalse(vm.state.value.loading)
        assertEquals(listOf("omar", "max"), vm.state.value.likes.map { it.profile.id })
        assertTrue(vm.state.value.likes.first().isNew)
    }

    @Test
    fun likeBackMakesTheMatchAndLeavesTheList() {
        val api = api()
        val matched = mutableListOf<String>()
        var changed = 0
        val vm = LikesYouViewModel(api, onMatched = { matched += it.id }, onChanged = { changed++ })
        vm.open("omar")
        assertEquals("omar", vm.state.value.open?.profile?.id)
        vm.likeBack("omar", "  Thank you!  ")
        assertEquals(listOf("omar" to "Thank you!"), api.likedBack)
        assertEquals(listOf("omar"), matched)
        assertEquals(listOf("max"), vm.state.value.likes.map { it.profile.id })
        assertNull(vm.state.value.open)  // back to the list
        assertNull(vm.state.value.workingOn)
        assertEquals(1, changed)
    }

    @Test
    fun passRemovesWithoutAMatch() {
        val api = api()
        val matched = mutableListOf<String>()
        val vm = LikesYouViewModel(api, onMatched = { matched += it.id })
        vm.pass("max")
        assertEquals(listOf("max"), api.passed)
        assertTrue(matched.isEmpty())
        assertEquals(listOf("omar"), vm.state.value.likes.map { it.profile.id })
    }

    @Test
    fun aLikeThatIsGoneIsRemovedWithAHint() {
        val api = api().apply { failNext = "ERROR: no_open_like" }
        val vm = LikesYouViewModel(api)
        vm.likeBack("omar")
        assertEquals("likes_you_gone", vm.state.value.errorKey)
        assertEquals(listOf("max"), vm.state.value.likes.map { it.profile.id })
    }

    @Test
    fun otherErrorsKeepThePerson() {
        val api = api().apply { failNext = "timeout" }
        val vm = LikesYouViewModel(api)
        vm.pass("omar")
        assertEquals("likes_you_failed", vm.state.value.errorKey)
        assertEquals(2, vm.state.value.likes.size)
        assertNull(vm.state.value.workingOn)
        vm.clearError()
        assertNull(vm.state.value.errorKey)
    }

    @Test
    fun quietReloadShowsNewLikesWithoutLoadingScreen() {
        val api = api()
        val vm = LikesYouViewModel(api)
        api.likes += like("leo", new = true)
        vm.load(quiet = true)
        assertFalse(vm.state.value.loading)
        assertEquals(3, vm.state.value.likes.size)
    }

    @Test
    fun filterWithAMessageAndSortOnlineFirst() {
        val api = FakeLikesApi().apply {
            likes += like("omar", "photo", "Lovely smile").let { it.copy(profile = it.profile.copy(activity = "offline")) }
            likes += like("max").let { it.copy(profile = it.profile.copy(activity = "online")) }
            likes += like("leo", comment = "Salam!").let { it.copy(profile = it.profile.copy(activity = "recent")) }
        }
        val vm = LikesYouViewModel(api)
        assertEquals(listOf("omar", "max", "leo"), vm.state.value.shown.map { it.profile.id })  // newest first
        vm.setOnlineFirst(true)
        assertEquals(listOf("max", "leo", "omar"), vm.state.value.shown.map { it.profile.id })
        vm.setWithMessageOnly(true)
        assertEquals(listOf("leo", "omar"), vm.state.value.shown.map { it.profile.id })
        vm.setOnlineFirst(false)
        assertEquals(listOf("omar", "leo"), vm.state.value.shown.map { it.profile.id })
        vm.setWithMessageOnly(false)
        assertEquals(3, vm.state.value.shown.size)
    }

    @Test
    fun newSortAndFilterTextsInBothLanguages() {
        listOf("likes_you_filter_all", "likes_you_filter_message", "likes_you_sort", "likes_you_sort_newest",
            "likes_you_sort_online", "likes_you_swipe_hint", "likes_you_none_with_message").forEach {
            assertNotEquals(it, Localization.t(it, AppLanguage.EN))
            assertNotEquals(it, Localization.t(it, AppLanguage.DE))
        }
    }

    @Test
    fun withoutServerItSaysSo() {
        val vm = LikesYouViewModel(null)
        assertTrue(vm.state.value.loadFailed)
    }

    @Test
    fun countTextIsSingularForOne() {
        assertEquals("1 person liked you", LikesYouText.count(1, AppLanguage.EN))
        assertEquals("3 people liked you", LikesYouText.count(3, AppLanguage.EN))
        assertEquals("1 Person mag Sie", LikesYouText.count(1, AppLanguage.DE))
        assertEquals("3 Personen mögen Sie", LikesYouText.count(3, AppLanguage.DE))
    }

    @Test
    fun whatWasLikedIsNamed() {
        val q = PromptCatalog.all.first()
        assertEquals("Liked your photo", LikesYouText.likedWhat(like("a", "photo"), AppLanguage.EN))
        assertEquals("Liked your Signature Card", LikesYouText.likedWhat(like("a", "card"), AppLanguage.EN))
        assertEquals("Liked your profile", LikesYouText.likedWhat(like("a"), AppLanguage.EN))
        assertEquals("Liked your answer to “${q.questionEn}”", LikesYouText.likedWhat(like("a", "prompt", promptKey = q.key), AppLanguage.EN))
        assertEquals("Gefällt Ihre Antwort auf „${q.questionDe}“", LikesYouText.likedWhat(like("a", "prompt", promptKey = q.key), AppLanguage.DE))
        assertEquals("Liked your answer", LikesYouText.likedWhat(like("a", "prompt", promptKey = "gone"), AppLanguage.EN))
    }

    @Test
    fun everyTextInBothLanguages() {
        listOf("likes_you_hint", "likes_you_empty_body", "likes_you_browse", "likes_you_load_failed", "likes_you_failed",
            "likes_you_gone", "likes_you_new", "likes_you_like_back", "likes_you_like_back_title", "likes_you_none").forEach {
            assertNotEquals(it, Localization.t(it, AppLanguage.EN))
            assertNotEquals(it, Localization.t(it, AppLanguage.DE))
            assertNotEquals(Localization.t(it, AppLanguage.EN), Localization.t(it, AppLanguage.DE))
        }
    }
}
