/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app

import com.vevak.app.data.ProtectionPromptPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionPromptPolicyTest {
    @Test
    fun oneContactMustReachTwoMessagesByItself() {
        val counts = mutableMapOf("alice" to 0, "bob" to 0)
        counts["alice"] = ProtectionPromptPolicy.increment(counts.getValue("alice"))
        counts["bob"] = ProtectionPromptPolicy.increment(counts.getValue("bob"))

        assertFalse(ProtectionPromptPolicy.isEligible(counts.getValue("alice"), dismissed = false))
        assertFalse(ProtectionPromptPolicy.isEligible(counts.getValue("bob"), dismissed = false))

        counts["alice"] = ProtectionPromptPolicy.increment(counts.getValue("alice"))
        assertTrue(ProtectionPromptPolicy.isEligible(counts.getValue("alice"), dismissed = false))
        assertFalse(ProtectionPromptPolicy.isEligible(counts.getValue("bob"), dismissed = false))
    }

    @Test
    fun dismissalIsPerContactAndCounterStaysBounded() {
        var count = 0
        repeat(8) { count = ProtectionPromptPolicy.increment(count) }

        assertTrue(ProtectionPromptPolicy.isEligible(count, dismissed = false))
        assertFalse(ProtectionPromptPolicy.isEligible(count, dismissed = true))
    }
}
