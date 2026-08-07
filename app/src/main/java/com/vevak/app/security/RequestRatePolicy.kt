/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.security

object RequestRatePolicy {
    fun isAllowed(previousMillis: Long, nowMillis: Long, minimumIntervalMillis: Long): Boolean {
        if (previousMillis <= 0L) return true
        val elapsed = nowMillis - previousMillis
        return elapsed < 0L || elapsed >= minimumIntervalMillis
    }
}
