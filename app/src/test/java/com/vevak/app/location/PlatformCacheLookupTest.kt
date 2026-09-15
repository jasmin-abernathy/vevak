package com.vevak.app.location

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PlatformCacheLookupTest {
    @Test fun silentCallbackAllowsDurableFallback() = runBlocking {
        var callbackCancelled = false
        val result = platformCacheLookup<String>(25) {
            try { awaitCancellation() } finally { callbackCancelled = true }
        } ?: "remembered-point"
        assertEquals("remembered-point", result)
        assertTrue(callbackCancelled)
    }

    @Test fun availableCacheIsPreserved() = runBlocking {
        assertEquals("platform-point", platformCacheLookup { "platform-point" })
    }

    @Test fun callerCancellationDoesNotTriggerFallback() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var fallbackReached = false
        val job = launch {
            platformCacheLookup<String>(60_000) { entered.complete(Unit); awaitCancellation() }
            fallbackReached = true
        }
        entered.await()
        job.cancelAndJoin()
        assertFalse(fallbackReached)
    }
}
