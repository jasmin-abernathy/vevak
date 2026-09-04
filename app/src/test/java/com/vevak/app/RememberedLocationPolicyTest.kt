/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app

import com.vevak.app.location.RememberedLocationPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RememberedLocationPolicyTest {
    @Test
    fun newerOrSameCaptureMayReplaceStoredPosition() {
        assertTrue(RememberedLocationPolicy.shouldReplace(null, 2_000L))
        assertTrue(RememberedLocationPolicy.shouldReplace(1_000L, 2_000L))
        assertTrue(RememberedLocationPolicy.shouldReplace(2_000L, 2_000L))
    }

    @Test
    fun olderCaptureNeverOverwritesNewerRememberedPosition() {
        assertFalse(RememberedLocationPolicy.shouldReplace(2_000L, 1_999L))
        assertFalse(RememberedLocationPolicy.shouldReplace(2_000L, 0L))
    }
}
