/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.system

import android.content.Context
import com.vevak.app.model.VeVakSettings

/**
 * Compatibility shim retained for code and migration stability.
 *
 * Since 0.3.11 VeVak has no request notification and no permanent status notification. Automatic
 * SMS replies are completely independent from Android notification permission. Emergency shortcut
 * feedback is also intentionally silent so a discreet shortcut cannot reveal VeVak after use.
 *
 * The methods remain as no-ops because older UI/view-model paths still call them; keeping this shim
 * avoids a risky broad refactor while making the new privacy contract explicit and testable.
 */
class RequestVisibilityNotifier(@Suppress("UNUSED_PARAMETER") context: Context) {
    fun ensureChannels() = Unit

    fun notificationsAllowedForRequests(@Suppress("UNUSED_PARAMETER") discreet: Boolean = false): Boolean = true

    fun syncActiveStatus(@Suppress("UNUSED_PARAMETER") settings: VeVakSettings) = Unit

    fun showRequestReceived(@Suppress("UNUSED_PARAMETER") discreet: Boolean = false): Boolean = true

    fun showEmergencyResult(
        @Suppress("UNUSED_PARAMETER") sentCount: Int,
        @Suppress("UNUSED_PARAMETER") targetCount: Int,
        @Suppress("UNUSED_PARAMETER") detail: String
    ) = Unit

    fun cancelActiveStatus() = Unit
}
