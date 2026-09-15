/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.vevak.app.emergency

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EmergencyPersistenceGuardTest {
    @Test fun failedClaimNeverReachesDispatchAndRejectsFurtherWrites() {
        val guard = EmergencyPersistenceGuard()
        var dispatches = 0
        assertThrows(IOException::class.java) {
            guard.persist { false }
            dispatches++
        }
        assertThrows(IOException::class.java) { guard.checkHealthy() }
        var retried = false
        assertThrows(IOException::class.java) { guard.persist { retried = true; true } }
        assertEquals(false, retried)
        assertEquals(0, dispatches)
    }

    @Test fun storageExceptionAlsoInvalidatesTheProcessState() {
        val guard = EmergencyPersistenceGuard()
        assertThrows(IllegalStateException::class.java) {
            guard.persist { throw IllegalStateException("storage failure") }
        }
        assertThrows(IOException::class.java) { guard.checkHealthy() }
    }

    @Test fun successfulWritesAllowSubsequentDurableTransitions() {
        val guard = EmergencyPersistenceGuard()
        var writes = 0
        repeat(3) { guard.persist { writes++; true } }
        guard.checkHealthy()
        assertEquals(3, writes)
    }
}
