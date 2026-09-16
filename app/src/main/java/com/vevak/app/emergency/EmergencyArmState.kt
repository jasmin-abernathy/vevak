/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.emergency

internal enum class EmergencyArmPhase { IDLE, CANCEL_WINDOW, PENDING_SYSTEM }

internal data class EmergencyArmState(
    val phase: EmergencyArmPhase,
    val remainingMillis: Long = 0L
) {
    val isCancellable: Boolean get() = phase != EmergencyArmPhase.IDLE
}

/** No expiry: an unconsumed arm remains cancellable on the same boot. */
internal object EmergencyArmStatePolicy {
    fun resolve(
        hasArm: Boolean,
        deadline: Long,
        now: Long,
        armedBootCount: Int,
        currentBootCount: Int,
        graceMillis: Long
    ): EmergencyArmState {
        if (!hasArm || deadline <= 0L ||
            (armedBootCount >= 0 && currentBootCount >= 0 && armedBootCount != currentBootCount)
        ) return EmergencyArmState(EmergencyArmPhase.IDLE)

        // Reject implausible future uptime from legacy state without a boot marker.
        if (deadline > now) {
            val remaining = deadline - now
            return if (remaining in 1L..graceMillis) {
                EmergencyArmState(EmergencyArmPhase.CANCEL_WINDOW, remaining)
            } else EmergencyArmState(EmergencyArmPhase.IDLE)
        }
        return EmergencyArmState(EmergencyArmPhase.PENDING_SYSTEM)
    }
}
