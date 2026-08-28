/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.security

import kotlin.math.max

data class RequestRateState(
    val lastAcceptedMillis: Long = 0L,
    val windowStartMillis: Long = 0L,
    val acceptedInWindow: Int = 0
)

data class RequestRateEvaluation(
    val allowed: Boolean,
    val state: RequestRateState
)

object RequestRatePolicy {
    const val HARD_MIN_INTERVAL_MILLIS = 15 * 60 * 1_000L
    const val WINDOW_MILLIS = 24 * 60 * 60 * 1_000L
    const val MAX_REQUESTS_PER_WINDOW = 4

    fun evaluate(
        state: RequestRateState,
        nowMillis: Long,
        configuredMinimumIntervalMillis: Long
    ): RequestRateEvaluation {
        if (nowMillis <= 0L) return RequestRateEvaluation(false, state)
        if (state.lastAcceptedMillis > 0L && nowMillis < state.lastAcceptedMillis) {
            return RequestRateEvaluation(false, state)
        }
        if (state.windowStartMillis > 0L && nowMillis < state.windowStartMillis) {
            return RequestRateEvaluation(false, state)
        }

        val minimumInterval = max(configuredMinimumIntervalMillis, HARD_MIN_INTERVAL_MILLIS)
        val windowExpired = state.windowStartMillis <= 0L ||
            nowMillis - state.windowStartMillis >= WINDOW_MILLIS
        val windowStart = if (windowExpired) nowMillis else state.windowStartMillis
        val count = if (windowExpired) 0 else state.acceptedInWindow.coerceAtLeast(0)

        if (state.lastAcceptedMillis > 0L && nowMillis - state.lastAcceptedMillis < minimumInterval) {
            return RequestRateEvaluation(false, state.copy(windowStartMillis = windowStart, acceptedInWindow = count))
        }
        if (count >= MAX_REQUESTS_PER_WINDOW) {
            return RequestRateEvaluation(false, state.copy(windowStartMillis = windowStart, acceptedInWindow = count))
        }

        return RequestRateEvaluation(
            allowed = true,
            state = RequestRateState(
                lastAcceptedMillis = nowMillis,
                windowStartMillis = windowStart,
                acceptedInWindow = count + 1
            )
        )
    }

    fun isAllowed(previousMillis: Long, nowMillis: Long, minimumIntervalMillis: Long): Boolean =
        evaluate(
            RequestRateState(lastAcceptedMillis = previousMillis),
            nowMillis,
            minimumIntervalMillis
        ).allowed
}
