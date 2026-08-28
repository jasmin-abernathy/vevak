/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.entitlement

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Play-flavor boundary for future Google Play Billing integration.
 *
 * No billing SDK is added yet: until a reviewed implementation lands, the
 * official Play build exposes the free tier. Proprietary store dependencies
 * must remain isolated to this flavor.
 */
class PlatformEntitlementProvider : EntitlementProvider {
    private val mutableState = MutableStateFlow(EntitlementState.free())
    override val state: StateFlow<EntitlementState> = mutableState.asStateFlow()

    override suspend fun refresh() = Unit
}
