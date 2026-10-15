package com.example

import com.example.data.MarryMatchRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SelfieVerificationTest {

    private lateinit var repository: MarryMatchRepository

    @Before
    fun setUp() {
        repository = MarryMatchRepository()
    }

    @Test
    fun testInitialUserIsUnverified() {
        val user = repository.currentUser.value
        assertFalse("User should initially not be selfie verified", user.selfieVerified)
    }

    @Test
    fun testSubmitSelfieVerificationEnqueuesRequest() {
        val pose = "Touch your nose with one finger"
        val photoUri = "content://media/external/images/media/123"

        val initialQueueSize = repository.verificationQueue.value.size
        repository.submitSelfieVerification(pose = pose, photoUri = photoUri, autoApprove = false)

        val queue = repository.verificationQueue.value
        assertEquals("Verification queue size should increase by 1", initialQueueSize + 1, queue.size)

        val latest = queue.first()
        assertEquals(pose, latest.pose)
        assertEquals(photoUri, latest.selfiePhotoUrl)
        assertEquals("pending", latest.status)
        assertFalse(repository.currentUser.value.selfieVerified)
    }

    @Test
    fun testCompleteSelfieVerificationFulfillsRequirement() {
        val pose = "Show a peace sign next to your cheek"
        val photoUri = "content://media/external/images/media/456"

        assertFalse("Initially unverified", repository.currentUser.value.selfieVerified)

        // Complete verification (as executed after biometric verification flow)
        repository.completeSelfieVerification(pose = pose, photoUri = photoUri)

        val user = repository.currentUser.value
        assertTrue("User selfie verified requirement must be fulfilled", user.selfieVerified)

        val latest = repository.verificationQueue.value.firstOrNull()
        assertEquals("approved", latest?.status)
    }

    @Test
    fun testModeratorApprovalFulfillsVerification() {
        val pose = "Thumbs up and tilt your head to the left"
        val photoUri = "content://media/external/images/media/789"

        repository.submitSelfieVerification(pose = pose, photoUri = photoUri, autoApprove = false)
        val requestId = repository.verificationQueue.value.first().id

        assertFalse(repository.currentUser.value.selfieVerified)

        // Admin approves request
        repository.reviewSelfie(requestId = requestId, approve = true)

        assertTrue("User should now be verified after admin approval", repository.currentUser.value.selfieVerified)
    }
}
