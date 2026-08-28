/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.entitlement

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform/store boundary for paid access.
 *
 * The core only depends on this interface. Store SDKs must stay in the
 * relevant product-flavor source set so the canonical FOSS build does not
 * acquire proprietary billing dependencies.
 */
interface EntitlementProvider {
    val state: StateFlow<EntitlementState>

    /** Refreshes store state when the platform implementation supports it. */
    suspend fun refresh()
}
