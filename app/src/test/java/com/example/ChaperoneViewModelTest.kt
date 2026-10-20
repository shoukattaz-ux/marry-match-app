package com.example

import com.example.data.ChaperoneApi
import com.example.data.ChaperoneInvite
import com.example.data.MyChaperone
import com.example.data.Ward
import com.example.data.WardMatch
import com.example.data.WardMessage
import com.example.ui.viewmodel.ChaperoneViewModel
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

/** Behaves like the chaperone functions of migration 020, in memory. */
private class FakeChaperoneApi : ChaperoneApi {
    var mine: MyChaperone? = null
    val wards = mutableListOf<Ward>()
    val validCodes = mutableMapOf("ABCD2345" to "Amira")
    val ended = mutableListOf<String>()
    var failNext: Throwable? = null
    private var n = 0

    private fun maybeFail() { failNext?.let { failNext = null; throw it } }

    override suspend fun invite(): ChaperoneInvite {
        maybeFail()
        if (mine?.isActive == true) throw IllegalStateException("already_has_chaperone")
        n++
        mine = MyChaperone("inv$n", "pending", expiresAt = "2026-10-18T12:00:00Z")
        return ChaperoneInvite("inv$n", "CODE000$n", "2026-10-18T12:00:00Z")
    }

    override suspend fun accept(code: String, yourName: String): String {
        maybeFail()
        val member = validCodes.remove(code.uppercase().replace("-", "")) ?: throw IllegalStateException("P0001: invalid_code")
        wards += Ward("g1", "w1", member, "2026-10-16T10:00:00Z", listOf(WardMatch("m1", "Malik", 30)))
        return member
    }

    override suspend fun end(id: String) {
        maybeFail()
        ended += id
        if (mine?.id == id) mine = null
        wards.removeAll { it.id == id }
    }

    override suspend fun myChaperone() = mine
    override suspend fun myWards() = wards.toList()
    override suspend fun wardMessages(matchId: String): List<WardMessage> {
        maybeFail()
        if (wards.none { w -> w.matches.any { it.matchId == matchId } }) throw IllegalStateException("not_allowed")
        return listOf(WardMessage("x1", fromMember = true, body = "Salam", sentAt = "2026-10-16T11:00:00Z"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChaperoneViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun memberCreatesAnInviteAndSeesTheCodeOnce() {
        val api = FakeChaperoneApi()
        val vm = ChaperoneViewModel(api, hasProfile = true)
        vm.createInvite()
        assertEquals("CODE0001", vm.state.value.invite?.code)
        assertEquals("pending", vm.state.value.mine?.status)

        // After reopening the screen the server only has the hash: the code is gone.
        val reopened = ChaperoneViewModel(api, hasProfile = true)
        assertNull(reopened.state.value.invite)
        assertEquals("pending", reopened.state.value.mine?.status)
    }

    @Test
    fun cancellingTheInvitationClearsIt() {
        val api = FakeChaperoneApi()
        val vm = ChaperoneViewModel(api, hasProfile = true)
        vm.createInvite()
        vm.endMine()
        assertEquals(listOf("inv1"), api.ended)
        assertNull(vm.state.value.mine)
        assertNull(vm.state.value.invite)
    }

    @Test
    fun acceptingAValidCodeAddsTheMemberAndCallsBack() {
        val api = FakeChaperoneApi()
        val vm = ChaperoneViewModel(api, hasProfile = false)
        var joined = false
        vm.accept("abcd-2345", "Uncle Ahmed") { joined = true }
        assertTrue(joined)
        assertEquals("Amira", vm.state.value.joinedMember)
        assertEquals(listOf("Amira"), vm.state.value.wards.map { it.memberName })
    }

    @Test
    fun aWrongCodeShowsTheServerReasonAndDoesNotCallBack() {
        val vm = ChaperoneViewModel(FakeChaperoneApi(), hasProfile = false)
        var joined = false
        vm.accept("WXYZ-2345", "Uncle Ahmed") { joined = true }
        assertFalse(joined)
        assertEquals("chaperone_error_invalid_code", vm.state.value.errorKey)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun aMistypedCodeIsCaughtBeforeAskingTheServer() {
        val api = FakeChaperoneApi()
        val vm = ChaperoneViewModel(api, hasProfile = false)
        vm.accept("ABCD-234", "Uncle Ahmed")
        assertEquals("chaperone_error_code_format", vm.state.value.errorKey)
        vm.accept("ABCD-0345", "Uncle Ahmed")
        assertEquals("chaperone_error_code_format", vm.state.value.errorKey)
        assertTrue(api.validCodes.containsKey("ABCD2345"))
    }

    @Test
    fun aPastedInvitationMessageWorks() {
        val api = FakeChaperoneApi()
        val vm = ChaperoneViewModel(api, hasProfile = false)
        var joined = false
        vm.accept("I'd like you to be my chaperone. Enter this code within 48 hours: ABCD-2345", "Mama") { joined = true }
        assertTrue(joined)
    }

    @Test
    fun aQuietRefreshKeepsTheMessageOnScreen() {
        val api = FakeChaperoneApi()
        val vm = ChaperoneViewModel(api, hasProfile = true)
        vm.accept("ABCD-234", "Mama")
        vm.refresh(quiet = true)
        assertEquals("chaperone_error_code_format", vm.state.value.errorKey)
    }

    @Test
    fun aMissingNameIsCaughtBeforeAskingTheServer() {
        val api = FakeChaperoneApi()
        val vm = ChaperoneViewModel(api, hasProfile = false)
        vm.accept("ABCD2345", "  ")
        assertEquals("chaperone_error_name_required", vm.state.value.errorKey)
        assertTrue(api.validCodes.containsKey("ABCD2345"))
    }

    @Test
    fun chaperoneReadsAChatAndCanLeave() {
        val api = FakeChaperoneApi()
        val vm = ChaperoneViewModel(api, hasProfile = false)
        vm.accept("ABCD2345", "Uncle Ahmed")
        val ward = vm.state.value.wards.single()
        vm.openChat(ward, ward.matches.single())
        assertEquals(listOf("Salam"), vm.state.value.messages.map { it.body })
        vm.closeChat()
        assertNull(vm.state.value.openMatch)

        vm.leave(ward)
        assertEquals(listOf("g1"), api.ended)
        assertTrue(vm.state.value.wards.isEmpty())
    }

    @Test
    fun aFailedLoadIsNotMistakenForNoMembers() {
        val api = FakeChaperoneApi()
        val vm = ChaperoneViewModel(object : ChaperoneApi by api {
            override suspend fun myWards(): List<Ward> = throw RuntimeException("network down")
        }, hasProfile = false)
        assertFalse(vm.state.value.loaded)
        assertEquals("chaperone_error_general", vm.state.value.errorKey)
    }

    @Test
    fun errorKeysMapKnownServerCodes() {
        assertEquals("chaperone_error_own_code", ChaperoneApi.errorKey(RuntimeException("P0001: own_code")))
        assertEquals("chaperone_error_general", ChaperoneApi.errorKey(RuntimeException("timeout")))
    }
}
