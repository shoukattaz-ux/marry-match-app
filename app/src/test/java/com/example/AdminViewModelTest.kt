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
    val users = mutableListOf(
        AdminUser("bad", "Max", email = "max@example.com", lastSignInAt = "2026-10-20T10:00:00Z"),
        AdminUser("admin", "Mod", email = "mod@example.com", isAdmin = true, isOwner = true, lastSignInAt = "2026-10-22T09:00:00Z"),
        AdminUser("new", "", email = "sara@example.de"),
        AdminUser("unconfirmed", "", email = "late@example.de", emailConfirmed = false, lastSignInAt = null)
    )
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
        maleSignupsPaused = malePaused,
        dailyLikeLimit = likeLimit,
        recentlyOnlineMinutes = recentMinutes,
        onlineMinutes = onlineMinutes
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
    override suspend fun searchUsers(query: String, signedInOnly: Boolean) = users.filter {
        (query.isBlank() || it.displayName.contains(query, true) || it.email.contains(query, true)) &&
            (!signedInOnly || it.lastSignInAt != null)
    }
    val createdAccounts = mutableListOf<com.example.data.NewAccount>()
    var createRefusal: String? = null
    override suspend fun createAccount(account: com.example.data.NewAccount) {
        createRefusal?.let { throw ModerationException(it) }
        createdAccounts += account
        users += AdminUser("new-${createdAccounts.size}", account.details.name, email = account.details.email, emailConfirmed = true)
    }

    var onlineMinutes = 10
    override suspend fun setOnlineMinutes(minutes: Int) {
        if (minutes !in 1..240) throw ModerationException("bad_request")
        onlineMinutes = minutes
    }
    var recentMinutes = 20
    override suspend fun setRecentMinutes(minutes: Int) {
        if (minutes !in 3..1440) throw ModerationException("bad_request")
        recentMinutes = minutes
    }
    var likeLimit = 10
    override suspend fun setLikeLimit(limit: Int) {
        failNext?.let { failNext = null; throw it }
        likeLimit = limit
    }
    val boosts = mutableListOf<Pair<String, Int>>()
    override suspend fun setBoost(userId: String, hours: Int) {
        if (hours !in 0..720) throw ModerationException("invalid_hours")
        boosts += userId to hours
        users.replaceAll { if (it.userId == userId) it.copy(boostUntil = if (hours > 0) "2026-11-01T18:00:00+00:00" else null) else it }
    }

    /** Who is calling: "admin" is the owner. */
    var caller = "admin"
    /** Like admin_set_admin: confirmed e-mail, not banned, the owner stays, only the owner removes admins. */
    override suspend fun setAdmin(userId: String, admin: Boolean) {
        val user = users.first { it.userId == userId }
        val callerIsOwner = users.first { it.userId == caller }.isOwner
        when {
            !admin && user.isOwner -> throw ModerationException("target_is_owner")
            !admin && userId == caller -> throw ModerationException("cannot_remove_self")
            !admin && !callerIsOwner -> throw ModerationException("owner_only")
            admin && !user.emailConfirmed -> throw ModerationException("email_not_confirmed")
            admin && user.isBanned -> throw ModerationException("user_banned")
        }
        users.replaceAll { if (it.userId == userId) it.copy(isAdmin = admin) else it }
    }
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
    fun searchByEmailAndOnlySignedIn() {
        val vm = AdminViewModel(FakeAdminApi())
        vm.setUserQuery("SARA@")
        vm.searchUsers()
        assertEquals(listOf("new"), vm.state.value.users.map { it.userId })
        vm.setUserQuery("")
        vm.setSignedInOnly(true)
        assertEquals(listOf("bad", "admin"), vm.state.value.users.map { it.userId })
    }

    @Test
    fun makeAndRemoveAdmin() {
        val api = FakeAdminApi()
        val vm = AdminViewModel(api)
        vm.searchUsers()
        vm.setAdmin(vm.state.value.users.first { it.userId == "new" }, true)
        assertEquals("admin_done_made_admin", vm.state.value.doneKey)
        assertTrue(vm.state.value.users.first { it.userId == "new" }.isAdmin)
        vm.setAdmin(vm.state.value.users.first { it.userId == "new" }, false)
        assertEquals("admin_done_removed_admin", vm.state.value.doneKey)
        assertEquals(false, vm.state.value.users.first { it.userId == "new" }.isAdmin)
    }

    @Test
    fun adminRefusalsAreExplained() {
        val vm = AdminViewModel(FakeAdminApi())
        vm.searchUsers()
        vm.setAdmin(vm.state.value.users.first { it.userId == "admin" }, false)
        assertEquals("admin_error_target_owner", vm.state.value.errorKey)
        vm.setAdmin(vm.state.value.users.first { it.userId == "unconfirmed" }, true)
        assertEquals("admin_error_not_confirmed", vm.state.value.errorKey)
        assertEquals(false, vm.state.value.users.first { it.userId == "unconfirmed" }.isAdmin)
    }

    @Test
    fun onlyTheOwnerRemovesAdmins() {
        val api = FakeAdminApi()
        val vm = AdminViewModel(api)
        vm.searchUsers()
        vm.setAdmin(vm.state.value.users.first { it.userId == "new" }, true)
        vm.setAdmin(vm.state.value.users.first { it.userId == "bad" }, true)
        api.caller = "new"  // a normal admin
        vm.setAdmin(vm.state.value.users.first { it.userId == "bad" }, false)
        assertEquals("admin_error_owner_only", vm.state.value.errorKey)
        assertTrue(vm.state.value.users.first { it.userId == "bad" }.isAdmin)
        vm.setAdmin(vm.state.value.users.first { it.userId == "admin" }, false)
        assertEquals("admin_error_target_owner", vm.state.value.errorKey)
        assertTrue(vm.state.value.users.first { it.userId == "admin" }.isAdmin)
    }

    private fun photo() = com.example.data.NewAccountPhoto(byteArrayOf(-1, -40, 1, 2), null, null, 0.5f, 0.4f, 1f)

    @Test
    fun createAnAccountFromPastedText() {
        val api = FakeAdminApi()
        val vm = AdminViewModel(api)
        val today = java.time.LocalDate.of(2026, 10, 30)
        vm.openCreate()
        vm.setCreateText(com.example.core.admin.AccountTemplate.TEMPLATE_EN.replace("Religion: Islam", "Religion: "))
        vm.checkCreate(today)
        assertTrue(vm.state.value.create!!.checked!!.ok)
        assertEquals(false, vm.state.value.create!!.canCreate)  // no photos yet
        vm.addCreatePhotos(listOf(photo(), photo()))
        assertEquals(false, vm.state.value.create!!.canCreate)  // 2 of at least 3
        vm.addCreatePhotos(listOf(photo()))
        assertTrue(vm.state.value.create!!.canCreate)
        vm.createAccount(today)
        val created = vm.state.value.create!!.created!!
        assertEquals("sara.beispiel@gmail.com", created.email)
        assertEquals("Sommer2026!", created.password)
        assertEquals("Sara", created.name)
        assertEquals(3, api.createdAccounts.single().photos.size)
        assertEquals("", api.createdAccounts.single().details.religion)
        vm.createAnother()
        assertEquals("", vm.state.value.create!!.text)
        vm.closeCreate()
        assertNull(vm.state.value.create)
    }

    @Test
    fun changingHowLongMembersStayOnline() {
        val api = FakeAdminApi()
        val vm = AdminViewModel(api)
        assertEquals(10, vm.state.value.stats?.onlineMinutes)
        vm.setOnlineMinutes(15)
        assertEquals(15, vm.state.value.stats?.onlineMinutes)
        assertEquals("admin_done_online_minutes", vm.state.value.doneKey)
        assertEquals(20, api.recentMinutes)  // the other setting is untouched
        vm.setOnlineMinutes(0)  // kept within 1 minute to 4 hours, like the server
        assertEquals(AdminViewModel.MIN_ONLINE_MINUTES, api.onlineMinutes)
        vm.setOnlineMinutes(1000)
        assertEquals(AdminViewModel.MAX_ONLINE_MINUTES, api.onlineMinutes)
    }

    @Test
    fun changingTheRecentlyOnlineTime() {
        val api = FakeAdminApi()
        val vm = AdminViewModel(api)
        assertEquals(20, vm.state.value.stats?.recentlyOnlineMinutes)
        vm.setRecentMinutes(45)
        assertEquals(45, vm.state.value.stats?.recentlyOnlineMinutes)
        assertEquals("admin_done_recent_minutes", vm.state.value.doneKey)
        vm.setRecentMinutes(1)  // kept within 3 minutes to 24 hours, like the server
        assertEquals(AdminViewModel.MIN_RECENT_MINUTES, api.recentMinutes)
    }

    @Test
    fun changingFreeLikesPerDay() {
        val api = FakeAdminApi()
        val vm = AdminViewModel(api)
        assertEquals(10, vm.state.value.stats?.dailyLikeLimit)
        vm.setLikeLimit(5)
        assertEquals(5, vm.state.value.stats?.dailyLikeLimit)
        assertEquals("admin_done_like_limit", vm.state.value.doneKey)
        vm.setLikeLimit(500)  // capped like on the server
        assertEquals(AdminViewModel.MAX_LIKE_LIMIT, api.likeLimit)
        vm.setLikeLimit(0)  // no limit
        assertEquals(0, vm.state.value.stats?.dailyLikeLimit)
    }

    @Test
    fun boostAndEndBoost() {
        val api = FakeAdminApi()
        val vm = AdminViewModel(api)
        vm.searchUsers()
        vm.setBoost(vm.state.value.users.first { it.userId == "bad" }, 72)
        assertEquals("admin_done_boosted", vm.state.value.doneKey)
        assertEquals("2026-11-01T18:00:00+00:00", vm.state.value.users.first { it.userId == "bad" }.boostUntil)
        vm.setBoost(vm.state.value.users.first { it.userId == "bad" }, 0)
        assertEquals("admin_done_boost_ended", vm.state.value.doneKey)
        assertNull(vm.state.value.users.first { it.userId == "bad" }.boostUntil)
        assertEquals(listOf("bad" to 72, "bad" to 0), api.boosts)
    }

    @Test
    fun aNewAccountCanStartBoosted() {
        val api = FakeAdminApi()
        val vm = AdminViewModel(api)
        val today = java.time.LocalDate.of(2026, 10, 30)
        vm.openCreate()
        vm.setCreateText(com.example.core.admin.AccountTemplate.TEMPLATE_EN)
        vm.checkCreate(today)
        vm.addCreatePhotos(List(3) { photo() })
        vm.setCreateBoost(24)
        vm.createAccount(today)
        assertEquals(24, api.createdAccounts.single().boostHours)
    }

    @Test
    fun createRefusalsAndLimits() {
        val api = FakeAdminApi().apply { createRefusal = "email_exists" }
        val vm = AdminViewModel(api)
        val today = java.time.LocalDate.of(2026, 10, 30)
        vm.openCreate()
        vm.setCreateText(com.example.core.admin.AccountTemplate.TEMPLATE_EN)
        vm.checkCreate(today)
        vm.addCreatePhotos(List(7) { photo() })
        assertEquals(6, vm.state.value.create!!.photos.size)
        assertEquals("create_err_photo_limit", vm.state.value.create!!.errorKey)
        vm.createAccount(today)
        assertEquals("create_srv_email_exists", vm.state.value.create!!.errorKey)
        assertNull(vm.state.value.create!!.created)
        // Changing the text needs a new check before creating.
        vm.setCreateText("Email: x")
        assertNull(vm.state.value.create!!.checked)
        assertEquals(false, vm.state.value.create!!.canCreate)
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
