/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.entitlement

/**
 * Stable core-facing entry point for entitlement state.
 *
 * The concrete provider is supplied by the active Gradle flavor.
 */
class EntitlementRepository(
    private val provider: EntitlementProvider = PlatformEntitlementProvider()
) {
    val state = provider.state

    suspend fun refresh() = provider.refresh()

    fun canUse(
        capability: PremiumCapability,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = PremiumAccessPolicy.canUse(capability, state.value, nowMillis)
}
