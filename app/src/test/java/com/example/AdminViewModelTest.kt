package com.example

import com.example.data.AdminApi
import com.example.data.AdminReport
import com.example.data.AdminStats
import com.example.data.AdminUser
import com.example.data.AuditEntry
import com.example.data.ModerationAction
import com.example.data.ModerationException
import com.example.ui.viewmodel.AdminViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Behaves like moderation_apply on the server, in memory. */
private class FakeAdminApi : AdminApi {
    val reports = mutableListOf(
        AdminReport("r1", "scam", reportedId = "bad", reportedName = "Max"),
        AdminReport("r2", "other", reportedId = "bad", reportedName = "Max"),
        AdminReport("r3", "fake", reportedId = "admin", reportedName = "Mod")
    )
    val users = mutableListOf(AdminUser("bad", "Max"), AdminUser("admin", "Mod", isAdmin = true))
    val calls = mutableListOf<String>()
    var failNext: Throwable? = null
    /** The Germany location check switch; on by default like on the server. */
    var locationCheck = true
    var switchCalls = 0
    /** New sign-ups from men; open by default like on the server. */
    var malePaused = false

    override suspend fun stats() = AdminStats(
        openReports = reports.count { it.status == "open" },
        banned = users.count { it.isBanned },
        locationCheck = locationCheck,
        men = 30, women = 12, men7d = 5, women7d = 2,
        maleSignupsPaused = malePaused
    )
    override suspend fun setMaleSignupsPaused(paused: Boolean) {
        failNext?.let { failNext = null; throw it }
        malePaused = paused
    }
    override suspend fun setLocationCheck(enabled: Boolean) {
        failNext?.let { failNext = null; throw it }
        switchCalls++
        locationCheck = enabled
    }
    override suspend fun reports(status: String) = reports.filter { it.status == status }
    override suspend fun searchUsers(query: String) = users.filter { query.isBlank() || it.displayName.contains(query, true) }
    override suspend fun auditLog(limit: Int) = listOf(AuditEntry(1, action = "user_ban"))
    override suspend fun moderate(action: ModerationAction, userId: String?, reportId: String?, note: String?) {
        failNext?.let { failNext = null; throw it }
        calls += "${action.wire}:${userId ?: reportId}:${note ?: ""}"
        fun setStatus(id: String?, status: String) {
            val i = reports.indexOfFirst { it.reportId == id && it.status == "open" }
            if (i < 0) throw ModerationException("report_not_open")
            reports[i] = reports[i].copy(status = status)
        }
        when (action) {
            ModerationAction.DISMISS -> setStatus(reportId, "dismissed")
            ModerationAction.WARN -> setStatus(reportId, "actioned")
            ModerationAction.BAN -> {
                if (users.first { it.userId == userId }.isAdmin) throw ModerationException("cannot_moderate_admin")
                users.replaceAll { if (it.userId == userId) it.copy(isBanned = true) else it }
                reports.replaceAll { if (it.reportedId == userId && it.status == "open") it.copy(status = "actioned", reportedBanned = true) else it }
            }
            ModerationAction.UNBAN -> users.replaceAll { if (it.userId == userId) it.copy(isBanned = false) else it }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {

    @Before
    fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsStatsAndReports() {
        val vm = AdminViewModel(FakeAdminApi())
        assertEquals(3, vm.state.value.stats?.openReports)
        vm.loadReports()
        assertEquals(listOf("r1", "r2", "r3"), vm.state.value.reports.map { it.reportId })
    }

    @Test
    fun dismissRemovesReportFromOpenList() {
        val api = FakeAdminApi()
        val vm = AdminViewModel(api)
        vm.loadReports()
        vm.dismissReport(vm.state.value.reports.first { it.reportId == "r2" })
        assertEquals(listOf("r1", "r3"), vm.state.value.reports.map { it.reportId })
        assertEquals("admin_done_dismissed", vm.state.value.doneKey)
        assertEquals(2, vm.state.value.stats?.openReports)
    }

    @Test
    fun banClosesAllReportsAboutThePerson() {
        val api = FakeAdminApi()
        val vm = AdminViewModel(api)
        vm.loadReports()
        vm.banReported(vm.state.value.reports.first(), note = null)
        assertEquals("ban:bad:scam", api.calls.single())
        assertEquals(listOf("r3"), vm.state.value.reports.map { it.reportId })
        assertEquals("admin_done_banned", vm.state.value.doneKey)
        assertNull(vm.state.value.workingOn)
    }

    @Test
    fun adminsCannotBeBanned() {
        val vm = AdminViewModel(FakeAdminApi())
        vm.loadReports()
        vm.banReported(vm.state.value.reports.first { it.reportId == "r3" }, "test")
        assertEquals("admin_error_admin", vm.state.value.errorKey)
        assertNull(vm.state.value.doneKey)
    }

    @Test
    fun reportHandledElsewhereShowsMessage() {
        val api = FakeAdminApi()
        val vm = AdminViewModel(api)
        vm.loadReports()
        val r1 = vm.state.value.reports.first()
        api.reports[0] = api.reports[0].copy(status = "dismissed")  // another moderator was faster
        vm.warnReported(r1)
        assertEquals("admin_error_already_done", vm.state.value.errorKey)
        assertEquals(listOf("r2", "r3"), vm.state.value.reports.map { it.reportId })
    }

    @Test
    fun searchBanAndUnbanUser() {
        val api = FakeAdminApi()
        val vm = AdminViewModel(api)
        vm.setUserQuery("max")
        vm.searchUsers()
        val max = vm.state.value.users.single()
        vm.setBanned(max, true, "fake photos")
        assertTrue(vm.state.value.users.single().isBanned)
        vm.setBanned(vm.state.value.users.single(), false, null)
        assertEquals(false, vm.state.value.users.single().isBanned)
        assertEquals(listOf("ban:bad:fake photos", "unban:bad:"), api.calls)
    }

    @Test
    fun networkErrorIsShown() {
        val api = FakeAdminApi().apply { failNext = RuntimeException("timeout") }
        val vm = AdminViewModel(api)
        vm.loadReports()
        vm.dismissReport(vm.state.value.reports.first())
        assertEquals("admin_action_failed", vm.state.value.errorKey)
        assertEquals(3, vm.state.value.reports.size)
    }

    @Test
    fun noServiceShowsLoadError() {
        val vm = AdminViewModel(null)
        assertEquals("admin_load_failed", vm.state.value.errorKey)
    }

    @Test
    fun locationCheckIsOnByDefault() {
        val vm = AdminViewModel(FakeAdminApi())
        assertEquals(true, vm.state.value.stats?.locationCheck)
    }

    @Test
    fun switchingTheLocationCheckOffAndOnUpdatesTheOverview() {
        val api = FakeAdminApi()
        val vm = AdminViewModel(api)
        vm.setLocationCheck(false)
        assertEquals(false, vm.state.value.stats?.locationCheck)
        assertEquals("admin_done_location_off", vm.state.value.doneKey)
        assertNull(vm.state.value.workingOn)

        vm.setLocationCheck(true)
        assertEquals(true, vm.state.value.stats?.locationCheck)
        assertEquals("admin_done_location_on", vm.state.value.doneKey)
        assertEquals(2, api.switchCalls)
    }

    @Test
    fun aRefusedSwitchChangeKeepsTheCheckOnAndShowsAnError() {
        val api = FakeAdminApi().apply { failNext = IllegalStateException("not_allowed") }
        val vm = AdminViewModel(api)
        vm.setLocationCheck(false)
        assertEquals("admin_action_failed", vm.state.value.errorKey)
        assertEquals(true, vm.state.value.stats?.locationCheck)
    }

    @Test
    fun theOverviewShowsMenAndWomenAndTheBalance() {
        val stats = AdminViewModel(FakeAdminApi()).state.value.stats!!
        assertEquals(30, stats.men)
        assertEquals(12, stats.women)
        assertEquals(2.5, stats.menPerWoman!!, 0.0001)
        assertEquals(false, stats.maleSignupsPaused)
        assertNull(AdminStats(men = 4).menPerWoman)
    }

    @Test
    fun pausingAndReopeningMaleSignUpsUpdatesTheOverview() {
        val api = FakeAdminApi()
        val vm = AdminViewModel(api)
        vm.setMaleSignupsPaused(true)
        assertEquals(true, vm.state.value.stats?.maleSignupsPaused)
        assertEquals("admin_done_male_paused", vm.state.value.doneKey)
        vm.setMaleSignupsPaused(false)
        assertEquals(false, vm.state.value.stats?.maleSignupsPaused)
        assertEquals("admin_done_male_open", vm.state.value.doneKey)
    }

    @Test
    fun aRefusedPauseKeepsSignUpsOpen() {
        val api = FakeAdminApi().apply { failNext = IllegalStateException("not_allowed") }
        val vm = AdminViewModel(api)
        vm.setMaleSignupsPaused(true)
        assertEquals("admin_action_failed", vm.state.value.errorKey)
        assertEquals(false, vm.state.value.stats?.maleSignupsPaused)
    }

    @Test
    fun theBalanceTextsExistInBothLanguages() {
        listOf(
            "male_signups_paused_title", "male_signups_paused", "signup_gender_required", "admin_balance_title",
            "admin_balance_pause_title", "admin_balance_confirm_body", "audit_setting_male_signups_paused",
            "audit_setting_male_signups_open", "admin_done_male_paused", "admin_done_male_open"
        ).forEach { key ->
            for (lang in com.example.core.AppLanguage.entries) {
                assertTrue("$lang $key", com.example.core.Localization.t(key, lang) != key)
            }
        }
        assertEquals("71% men · 29% women", String.format(com.example.core.Localization.t("admin_balance_share", com.example.core.AppLanguage.EN), 71, 29))
    }
}
