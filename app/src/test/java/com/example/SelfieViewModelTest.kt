package com.example

import com.example.core.PreparedPhoto
import com.example.data.SelfieReviewItem
import com.example.data.SelfieStatus
import com.example.data.SelfieTicket
import com.example.data.VerificationApi
import com.example.ui.viewmodel.SelfieQueueViewModel
import com.example.ui.viewmodel.SelfieStep
import com.example.ui.viewmodel.SelfieViewModel
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

private class FakeVerificationApi(var status: SelfieStatus = SelfieStatus(SelfieStatus.NONE)) : VerificationApi {
    var consentGiven = false
    var startCalls = 0
    var startError: String? = null
    var submitError: String? = null
    val submitted = mutableListOf<Pair<String, Int>>()
    val queue = mutableListOf(item("r1"), item("r2"))
    val decisions = mutableListOf<Triple<String, Boolean, String?>>()
    var reviewError: String? = null

    override suspend fun giveConsent() { consentGiven = true }
    override suspend fun startCheck(): SelfieTicket {
        startError?.let { throw IllegalStateException(it) }
        if (!consentGiven) throw IllegalStateException("consent_required")
        startCalls++
        return SelfieTicket("req-$startCalls", "touch_nose")
    }
    override suspend fun uploadAndSubmit(ticket: SelfieTicket, jpeg: ByteArray) {
        submitError?.let { throw IllegalStateException(it) }
        submitted += ticket.requestId to jpeg.size
    }
    override suspend fun myStatus() = status
    override suspend fun reviewQueue() = queue.toList()
    override suspend fun selfieUrl(item: SelfieReviewItem) = "https://signed/${item.requestId}"
    override suspend fun review(requestId: String, approve: Boolean, reason: String?) {
        reviewError?.let { throw IllegalStateException(it) }
        decisions += Triple(requestId, approve, reason)
        queue.removeAll { it.requestId == requestId }
    }
}

private fun item(id: String) = SelfieReviewItem(id, "u-$id", "Anna", 29, "Köln", "peace_sign", "2026-10-15T09:00:00Z", "u-$id/$id.jpg", emptyList())

private fun photo(faces: Int?) = PreparedPhoto(ByteArray(10), faces)

@OptIn(ExperimentalCoroutinesApi::class)
class SelfieViewModelTest {

    @Before
    fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun cannotStartWithoutConsent() {
        val api = FakeVerificationApi()
        val vm = SelfieViewModel(api)
        assertEquals(SelfieStep.INTRO, vm.state.value.step)
        vm.start()
        assertEquals(0, api.startCalls)
        assertEquals(SelfieStep.INTRO, vm.state.value.step)
    }

    @Test
    fun fullFlowEndsInReviewNotInBadge() {
        val api = FakeVerificationApi()
        val vm = SelfieViewModel(api)
        vm.setConsent(true)
        vm.start()
        assertTrue(api.consentGiven)
        assertEquals(SelfieStep.CAMERA, vm.state.value.step)
        assertEquals("touch_nose", vm.state.value.pose)

        vm.onPhotoTaken(photo(1))
        assertEquals(SelfieStep.REVIEW, vm.state.value.step)
        vm.submit()
        assertEquals(listOf("req-1" to 10), api.submitted)
        assertEquals("The app never grants the badge itself", SelfieStep.PENDING, vm.state.value.step)
    }

    @Test
    fun photoNeedsExactlyOneFace() {
        val api = FakeVerificationApi()
        val vm = SelfieViewModel(api)
        vm.setConsent(true)
        vm.start()
        vm.onPhotoTaken(photo(0))
        assertEquals("selfie_error_no_face", vm.state.value.errorKey)
        assertEquals(SelfieStep.CAMERA, vm.state.value.step)
        vm.onPhotoTaken(photo(2))
        assertEquals("selfie_error_many_faces", vm.state.value.errorKey)
        // Detector unavailable: the moderator still checks the photo.
        vm.onPhotoTaken(photo(null))
        assertEquals(SelfieStep.REVIEW, vm.state.value.step)
    }

    @Test
    fun retakeKeepsTheSameAttempt() {
        val api = FakeVerificationApi()
        val vm = SelfieViewModel(api)
        vm.setConsent(true)
        vm.start()
        vm.onPhotoTaken(photo(1))
        vm.retake()
        vm.onPhotoTaken(photo(1))
        vm.submit()
        assertEquals(1, api.startCalls)
        assertEquals("req-1", api.submitted.single().first)
    }

    @Test
    fun attemptLimitIsShown() {
        val api = FakeVerificationApi().apply { startError = "selfie_attempt_limit" }
        val vm = SelfieViewModel(api)
        vm.setConsent(true)
        vm.start()
        assertEquals("selfie_error_limit", vm.state.value.errorKey)
        assertEquals(SelfieStep.INTRO, vm.state.value.step)
    }

    @Test
    fun expiredCheckGoesBackToStart() {
        val api = FakeVerificationApi()
        val vm = SelfieViewModel(api)
        vm.setConsent(true)
        vm.start()
        vm.onPhotoTaken(photo(1))
        api.submitError = "selfie_request_expired"
        vm.submit()
        assertEquals(SelfieStep.INTRO, vm.state.value.step)
        assertEquals("selfie_error_expired", vm.state.value.errorKey)
        assertNull(vm.state.value.photo)
    }

    @Test
    fun networkErrorOnSubmitKeepsThePhoto() {
        val api = FakeVerificationApi().apply { }
        val vm = SelfieViewModel(api)
        vm.setConsent(true)
        vm.start()
        vm.onPhotoTaken(photo(1))
        api.submitError = "timeout"
        vm.submit()
        assertEquals(SelfieStep.REVIEW, vm.state.value.step)
        assertEquals("selfie_error_network", vm.state.value.errorKey)
        api.submitError = null
        vm.submit()
        assertEquals(SelfieStep.PENDING, vm.state.value.step)
    }

    @Test
    fun opensOnServerStatus() {
        assertEquals(SelfieStep.PENDING, SelfieViewModel(FakeVerificationApi(SelfieStatus(SelfieStatus.PENDING))).state.value.step)
        assertEquals(SelfieStep.VERIFIED, SelfieViewModel(FakeVerificationApi(SelfieStatus(SelfieStatus.APPROVED))).state.value.step)
        val rejected = SelfieViewModel(FakeVerificationApi(SelfieStatus(SelfieStatus.REJECTED, "pose_mismatch"))).state.value
        assertEquals(SelfieStep.INTRO, rejected.step)
        assertEquals("pose_mismatch", rejected.lastRejectReason)
    }

    @Test
    fun alreadyPendingOnServerShowsPending() {
        val api = FakeVerificationApi().apply { startError = "already_pending" }
        val vm = SelfieViewModel(api)
        vm.setConsent(true)
        vm.start()
        assertEquals(SelfieStep.PENDING, vm.state.value.step)
    }

    @Test
    fun moderatorApprovesAndRejectsWithReason() {
        val api = FakeVerificationApi()
        val vm = SelfieQueueViewModel(api)
        assertEquals(listOf("r1", "r2"), vm.state.value.items.map { it.requestId })

        vm.open(vm.state.value.items[0])
        assertEquals("https://signed/r1", vm.state.value.openSelfieUrl)
        vm.decide(approve = true)
        assertNull(vm.state.value.open)
        assertEquals(listOf("r2"), vm.state.value.items.map { it.requestId })

        vm.open(vm.state.value.items[0])
        vm.decide(approve = false)  // a rejection needs a reason
        assertTrue(api.decisions.size == 1)
        vm.decide(approve = false, reason = "pose_mismatch")
        assertEquals(Triple("r2", false, "pose_mismatch"), api.decisions.last())
        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test
    fun failedDecisionReloadsTheQueue() {
        val api = FakeVerificationApi()
        val vm = SelfieQueueViewModel(api)
        vm.open(vm.state.value.items[0])
        api.reviewError = "409"
        api.queue.removeAt(0)  // another moderator decided first
        vm.decide(approve = true)
        assertEquals("admin_decision_failed", vm.state.value.errorKey)
        assertEquals(listOf("r2"), vm.state.value.items.map { it.requestId })
        assertFalse(vm.state.value.deciding)
    }
}
