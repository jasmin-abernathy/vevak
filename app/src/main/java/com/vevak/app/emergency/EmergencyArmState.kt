/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.emergency

internal enum class EmergencyArmPhase { IDLE, PENDING_SYSTEM }

internal data class EmergencyArmState(val phase: EmergencyArmPhase) {
    val isCancellable: Boolean get() = phase != EmergencyArmPhase.IDLE
}

/** No expiry: an unconsumed arm remains cancellable on the same boot. */
internal object EmergencyArmStatePolicy {
    fun resolve(
        hasArm: Boolean,
        deadline: Long,
        now: Long,
        armedBootCount: Int,
        currentBootCount: Int
    ): EmergencyArmState {
        if (!hasArm || deadline <= 0L ||
            (armedBootCount >= 0 && currentBootCount >= 0 && armedBootCount != currentBootCount)
        ) return EmergencyArmState(EmergencyArmPhase.IDLE)

        // A valid arm, including one left by the old delayed version during upgrade, is
        // dispatchable at once. Still reject an implausibly distant future legacy timestamp.
        if (deadline > now && deadline - now > 30_000L) {
            return EmergencyArmState(EmergencyArmPhase.IDLE)
        }
        return EmergencyArmState(EmergencyArmPhase.PENDING_SYSTEM)
    }
}
