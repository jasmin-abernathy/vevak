/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app

import com.vevak.app.entitlement.EntitlementSource
import com.vevak.app.entitlement.EntitlementState
import com.vevak.app.entitlement.EntitlementTier
import com.vevak.app.entitlement.PremiumAccessPolicy
import com.vevak.app.entitlement.PremiumCapability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementPolicyTest {
    @Test
    fun freeTierCannotUsePremiumCapabilities() {
        assertFalse(
            PremiumAccessPolicy.canUse(
                PremiumCapability.MultipleTrustedPlaces,
                EntitlementState.free(),
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun premiumWithoutExpiryCanUsePremiumCapabilities() {
        val premium = EntitlementState(
            tier = EntitlementTier.Premium,
            source = EntitlementSource.PlayStore
        )
        assertTrue(
            PremiumAccessPolicy.canUse(
                PremiumCapability.AdvancedResponseProfiles,
                premium,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun expiredPremiumAccessFailsClosed() {
        val expired = EntitlementState(
            tier = EntitlementTier.Premium,
            source = EntitlementSource.PlayStore,
            validUntilEpochMs = 10_000L
        )
        assertFalse(
            PremiumAccessPolicy.canUse(
                PremiumCapability.OptionalRelayService,
                expired,
                nowMillis = 10_000L
            )
        )
    }
}
