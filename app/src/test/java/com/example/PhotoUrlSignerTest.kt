package com.example

import com.example.data.PhotoUrlSigner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoUrlSignerTest {

    private var now = 0L
    private val requests = mutableListOf<List<String>>()
    private var refused = setOf<String>()
    private var failBatches = false

    private val signer = PhotoUrlSigner(
        sign = { paths, seconds ->
            requests += paths
            if (failBatches && paths.size > 1) throw IllegalStateException("one path refused")
            paths.filter { it !in refused }.associateWith { "https://signed/$it?t=$now&s=$seconds" }
        },
        nowMs = { now }
    )

    @Test
    fun signsAllPathsInOneRequestAndKeepsOrder() = runTest {
        val urls = signer.urls(listOf("a/1.jpg", "a/2.jpg", "b/1.jpg"))
        assertEquals(1, requests.size)
        assertEquals(listOf("https://signed/a/1.jpg?t=0&s=3600", "https://signed/a/2.jpg?t=0&s=3600", "https://signed/b/1.jpg?t=0&s=3600"), urls)
    }

    @Test
    fun reusesLinksWhileValid() = runTest {
        signer.urls(listOf("a/1.jpg"))
        now = 45 * 60 * 1000L  // 15 minutes left: still reused
        assertEquals("https://signed/a/1.jpg?t=0&s=3600", signer.url("a/1.jpg"))
        assertEquals(1, requests.size)
        now = 55 * 60 * 1000L  // 5 minutes left: signed again
        assertEquals("https://signed/a/1.jpg?t=3300000&s=3600", signer.url("a/1.jpg"))
        assertEquals(2, requests.size)
    }

    @Test
    fun onlyMissingPathsAreRequested() = runTest {
        signer.urls(listOf("a/1.jpg"))
        signer.urls(listOf("a/1.jpg", "a/2.jpg"))
        assertEquals(listOf(listOf("a/1.jpg"), listOf("a/2.jpg")), requests)
    }

    @Test
    fun refusedPathsAreLeftOut() = runTest {
        refused = setOf("x/1.jpg")
        assertEquals(listOf("https://signed/a/1.jpg?t=0&s=3600"), signer.urls(listOf("x/1.jpg", "a/1.jpg")))
        assertNull(signer.url("x/1.jpg"))
    }

    @Test
    fun failedBatchFallsBackToSingleRequests() = runTest {
        failBatches = true
        val urls = signer.urls(listOf("a/1.jpg", "a/2.jpg"))
        assertEquals(2, urls.size)
        assertEquals(listOf(listOf("a/1.jpg", "a/2.jpg"), listOf("a/1.jpg"), listOf("a/2.jpg")), requests)
    }

    @Test
    fun clearForgetsLinks() = runTest {
        signer.urls(listOf("a/1.jpg"))
        signer.clear()
        signer.urls(listOf("a/1.jpg"))
        assertEquals(2, requests.size)
    }

    @Test
    fun refusedPathsAreNotAskedAgainForAWhile() = runTest {
        refused = setOf("a/1_t.jpg")
        signer.urls(listOf("a/1_t.jpg"))
        signer.urls(listOf("a/1_t.jpg"))
        assertEquals("refused path asked only once", 1, requests.size)
        now = 11 * 60 * 1000L
        refused = emptySet()
        assertEquals("https://signed/a/1_t.jpg?t=660000&s=3600", signer.url("a/1_t.jpg"))
    }

    @Test
    fun networkErrorsAreRetriedRightAway() = runTest {
        var offline = true
        val flaky = PhotoUrlSigner(sign = { paths, _ ->
            if (offline) throw java.io.IOException("offline")
            paths.associateWith { "https://signed/$it" }
        }, nowMs = { now })
        assertEquals(emptyList<String>(), flaky.urls(listOf("a/1.jpg")))
        offline = false
        assertEquals(listOf("https://signed/a/1.jpg"), flaky.urls(listOf("a/1.jpg")))
    }
}
