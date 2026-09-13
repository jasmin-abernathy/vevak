/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyArmStatePolicyTest {
    private fun state(now: Long, hasArm: Boolean = true, boot: Int = 7, currentBoot: Int = 7) =
        EmergencyArmStatePolicy.resolve(hasArm, 104_000L, now, boot, currentBoot, 4_000L)

    @Test fun graceBoundaryBecomesCancellablePendingWithoutExpiry() {
        assertEquals(EmergencyArmState(EmergencyArmPhase.CANCEL_WINDOW, 4_000L), state(100_000L))
        assertEquals(EmergencyArmState(EmergencyArmPhase.CANCEL_WINDOW, 1L), state(103_999L))
        for (now in listOf(104_000L, 104_001L, 86_504_000L)) {
            assertEquals(EmergencyArmPhase.PENDING_SYSTEM, state(now).phase)
            assertEquals(0L, state(now).remainingMillis)
            assertTrue(state(now).isCancellable)
        }
    }

    @Test fun absentArmIsIdleEvenWithAnExpiredDeadline() {
        val absent = state(200_000L, hasArm = false)
        assertEquals(EmergencyArmPhase.IDLE, absent.phase)
        assertFalse(absent.isCancellable)
    }

    @Test fun differentBootRejectsBothCoincidentGraceAndOldPending() {
        assertEquals(EmergencyArmPhase.IDLE, state(101_000L, currentBoot = 8).phase)
        assertEquals(EmergencyArmPhase.IDLE, state(200_000L, currentBoot = 8).phase)
    }

    @Test fun unknownBootPreservesLegacyWindowAndPendingButRejectsFarFuture() {
        assertEquals(EmergencyArmPhase.CANCEL_WINDOW, state(101_000L, boot = -1).phase)
        assertEquals(EmergencyArmPhase.PENDING_SYSTEM, state(200_000L, boot = -1).phase)
        assertEquals(EmergencyArmPhase.IDLE, state(1_000L, boot = -1).phase)
    }

    @Test fun invalidDeadlineCannotCreatePending() {
        assertEquals(EmergencyArmPhase.IDLE,
            EmergencyArmStatePolicy.resolve(true, 0L, 10L, 7, 7, 4_000L).phase)
    }
}
