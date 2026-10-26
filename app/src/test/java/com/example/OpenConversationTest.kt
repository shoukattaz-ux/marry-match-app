package com.example

import com.example.push.OpenConversation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OpenConversationTest {
    @Test
    fun aPushForTheOpenChatReloadsItInsteadOfANotification() = runTest(UnconfinedTestDispatcher()) {
        val reloads = mutableListOf<String>()
        val job = launch { OpenConversation.arrived.collect { reloads += it } }
        OpenConversation.opened("match-1")
        assertTrue(OpenConversation.onPush("match-1"))   // on screen: no notification
        assertFalse(OpenConversation.onPush("match-2"))  // another chat: notification
        assertFalse(OpenConversation.onPush(null))
        OpenConversation.closed("match-2")               // closing another chat changes nothing
        assertTrue(OpenConversation.onPush("match-1"))
        OpenConversation.closed("match-1")               // app in background or chat left
        assertFalse(OpenConversation.onPush("match-1"))
        assertEquals(listOf("match-1", "match-1"), reloads)
        job.cancel()
    }
}
