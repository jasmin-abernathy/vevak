/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.entitlement

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Canonical FOSS implementation: no proprietary store SDK and no network
 * dependency. Official FOSS builds expose the free tier unless a future,
 * explicitly reviewed open entitlement mechanism is added.
 */
class PlatformEntitlementProvider : EntitlementProvider {
    private val mutableState = MutableStateFlow(EntitlementState.free())
    override val state: StateFlow<EntitlementState> = mutableState.asStateFlow()

    override suspend fun refresh() = Unit
}
