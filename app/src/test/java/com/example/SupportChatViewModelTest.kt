package com.example

import com.example.data.AdminApi
import com.example.data.AdminReport
import com.example.data.AdminStats
import com.example.data.AdminUser
import com.example.data.AuditEntry
import com.example.data.ModerationAction
import com.example.data.SupportApi
import com.example.data.SupportException
import com.example.data.SupportMessage
import com.example.data.SupportThread
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.SupportChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

/** Behaves like the admin message functions of migration 037, in memory. */
private class FakeSupportApi(var me: String = "member") : SupportApi {
    val messages = mutableListOf<SupportMessage>()
    val threads = mutableListOf<SupportThread>()
    val live = MutableSharedFlow<SupportMessage>(extraBufferCapacity = 8)
    var closed = false
    var readCalls = 0
    var mutedCalls = mutableListOf<Boolean>()
    var sentPhotos = 0
    private var next = 0

    override suspend fun myThreads() = threads
    override suspend fun adminThreads() = threads
    override suspend fun openThread(memberId: String): String =
        threads.firstOrNull { it.otherId == memberId }?.threadId
            ?: "t-$memberId".also { threads += SupportThread(it, memberId, name = "M", mine = true) }
    override suspend fun messages(threadId: String) = messages.filter { it.threadId == threadId }
    override suspend fun send(threadId: String, memberId: String, body: String?, photoJpeg: ByteArray?): SupportMessage {
        if (closed) throw SupportException("conversation_closed")
        if (photoJpeg != null) sentPhotos++
        val m = SupportMessage("m${next++}", threadId, me, body, photoJpeg?.let { "$memberId/p.jpg" }, "2026-10-23T10:00:0${next}Z")
        messages += m
        return m
    }
    override suspend fun markRead(threadId: String) { readCalls++ }
    override suspend fun setMuted(threadId: String, muted: Boolean) { mutedCalls += muted }
    override fun changes(threadId: String): Flow<SupportMessage> = live
    override suspend fun photoUrl(path: String): String? = "https://x/$path"
}

private class NoAdminApi : AdminApi {
    val users = listOf(AdminUser("member", "Sara", email = "sara@example.de"))
    override suspend fun stats() = AdminStats()
    override suspend fun reports(status: String) = emptyList<AdminReport>()
    override suspend fun searchUsers(query: String, signedInOnly: Boolean) = users
    override suspend fun setAdmin(userId: String, admin: Boolean) {}
    override suspend fun auditLog(limit: Int) = emptyList<AuditEntry>()
    override suspend fun setLocationCheck(enabled: Boolean) {}
    override suspend fun setMaleSignupsPaused(paused: Boolean) {}
    override suspend fun moderate(action: ModerationAction, userId: String?, reportId: String?, note: String?) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class SupportChatViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun adminMessage(id: String, read: Boolean = false) =
        SupportMessage(id, "t1", "admin", "How do you like the app?", null, "2026-10-23T09:00:00Z", if (read) "2026-10-23T09:01:00Z" else null)

    @Test
    fun loadsHistoryAndMarksTheAdminsMessageRead() {
        val api = FakeSupportApi().apply { messages += adminMessage("a1") }
        val vm = SupportChatViewModel("t1", "member", "member", api, canWrite = true)
        assertEquals(listOf("a1"), vm.state.value.messages.map { it.id })
        assertFalse(vm.state.value.loading)
        assertEquals(1, api.readCalls)
    }

    @Test
    fun anotherAdminReadingAlongDoesNotMarkRead() {
        val api = FakeSupportApi(me = "admin2").apply { messages += adminMessage("a1") }
        val vm = SupportChatViewModel("t1", "member", "admin2", api, canWrite = false)
        assertEquals(0, api.readCalls)
        assertFalse(vm.send("hi"))
        assertTrue(api.messages.size == 1)
    }

    @Test
    fun replyWithTextAndPhoto() {
        val api = FakeSupportApi()
        val vm = SupportChatViewModel("t1", "member", "member", api, canWrite = true)
        assertTrue(vm.send("  The chat crashes when I send a photo  "))
        assertEquals("The chat crashes when I send a photo", vm.state.value.messages.single().body)
        assertTrue(vm.send("", photo = byteArrayOf(1, 2, 3)))
        assertEquals("member/p.jpg", vm.state.value.messages.last().photoPath)
        assertEquals(1, api.sentPhotos)
        assertFalse(vm.send("   "))
        assertEquals(2, vm.state.value.messages.size)
    }

    @Test
    fun liveMessagesAppearOnceAndAreMarkedRead() {
        val api = FakeSupportApi()
        val vm = SupportChatViewModel("t1", "member", "member", api, canWrite = true)
        api.live.tryEmit(adminMessage("a2"))
        api.live.tryEmit(adminMessage("a2", read = true))  // the same message, now read
        assertEquals(1, vm.state.value.messages.size)
        assertTrue(vm.state.value.messages.single().readAt != null)
        assertEquals(1, api.readCalls)
    }

    @Test
    fun aClosedConversationExplainsAndStopsWriting() {
        val api = FakeSupportApi().apply { closed = true }
        val vm = SupportChatViewModel("t1", "member", "member", api, canWrite = true)
        vm.send("hello")
        assertEquals("support_closed", vm.state.value.errorKey)
        assertFalse(vm.state.value.open)
        assertFalse(vm.send("again"))
    }

    @Test
    fun tooLongIsRefusedBeforeSending() {
        val api = FakeSupportApi()
        val vm = SupportChatViewModel("t1", "member", "member", api, canWrite = true)
        assertFalse(vm.send("x".repeat(SupportChatViewModel.MAX_LENGTH + 1)))
        assertEquals("support_error_too_long", vm.state.value.errorKey)
        assertTrue(api.messages.isEmpty())
    }

    @Test
    fun muting() {
        val api = FakeSupportApi()
        val vm = SupportChatViewModel("t1", "member", "member", api, canWrite = true)
        vm.setMuted(true)
        assertTrue(vm.state.value.muted)
        assertEquals(listOf(true), api.mutedCalls)
    }

    @Test
    fun adminStartsAConversationFromTheUserList() {
        val support = FakeSupportApi(me = "admin")
        val vm = AdminViewModel(NoAdminApi(), support)
        vm.searchUsers()
        vm.messageUser(vm.state.value.users.single())
        val open = vm.state.value.openThread!!
        assertEquals("member", open.otherId)
        assertTrue(open.mine)
        assertNull(vm.state.value.workingOn)
        // Opening again reuses the same conversation.
        vm.closeSupportThread()
        vm.messageUser(vm.state.value.users.single())
        assertEquals(open.threadId, vm.state.value.openThread!!.threadId)
        assertEquals(1, support.threads.size)
    }
}
