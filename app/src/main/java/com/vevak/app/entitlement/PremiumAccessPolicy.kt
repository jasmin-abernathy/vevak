/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.entitlement

/**
 * Central gate for optional paid capabilities.
 *
 * Core VeVak safety features intentionally never call this policy. This keeps
 * SMS request/reply, manual sharing, revocation, consent expiry, abuse
 * prevention and required diagnostics outside the paywall by construction.
 */
object PremiumAccessPolicy {
    fun canUse(
        capability: PremiumCapability,
        entitlement: EntitlementState,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        @Suppress("UNUSED_VARIABLE")
        val documentedCapability = capability
        return entitlement.hasPremiumAccess(nowMillis)
    }
}
