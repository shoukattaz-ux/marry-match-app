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

    override suspend fun stats() = AdminStats(openReports = reports.count { it.status == "open" }, banned = users.count { it.isBanned })
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
}
