/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.entitlement

/**
 * Capabilities that may be offered as paid convenience or service extensions.
 *
 * Listing a capability here does not mean it is implemented or promised.
 * Core safety behaviour must never depend on this enum.
 */
enum class PremiumCapability {
    MultipleTrustedContacts,
    EncryptedConfigurationExport,
    AdvancedPersonalisation,
    OptionalRelayService
}

enum class EntitlementTier {
    Free,
    Premium
}

enum class EntitlementSource {
    None,
    PlayStore,
    ExternalStore,
    Development
}

data class EntitlementState(
    val tier: EntitlementTier = EntitlementTier.Free,
    val source: EntitlementSource = EntitlementSource.None,
    val validUntilEpochMs: Long? = null
) {
    fun hasPremiumAccess(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (tier != EntitlementTier.Premium) return false
        return validUntilEpochMs?.let { nowMillis < it } ?: true
    }

    companion object {
        fun free(): EntitlementState = EntitlementState()
    }
}
