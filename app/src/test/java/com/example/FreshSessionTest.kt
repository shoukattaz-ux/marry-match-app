package com.example

import com.example.data.FreshSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshSessionTest {

    @Test
    fun aTokenIsRenewedShortlyBeforeItRunsOut() {
        val now = 1_000_000L
        assertFalse(FreshSession.expiresSoon(now + 3600, now))  // fresh: one hour left
        assertFalse(FreshSession.expiresSoon(now + FreshSession.EARLY_SECONDS + 1, now))
        assertTrue(FreshSession.expiresSoon(now + FreshSession.EARLY_SECONDS, now))
        assertTrue(FreshSession.expiresSoon(now - 600, now))  // already expired, e.g. after a night in the background
    }
}
