/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.vevak.app.location

import kotlinx.coroutines.withTimeoutOrNull

/**
 * A silent/missing platform cache must not hold up the durable VeVak fallback.
 * Bounds cancellable callbacks (not arbitrary blocking platform code).
 * Parent cancellation still propagates; only this lookup's deadline means cache unavailable.
 */
internal suspend fun <T> platformCacheLookup(
    timeoutMillis: Long = 1_500L,
    read: suspend () -> T?
): T? = withTimeoutOrNull(timeoutMillis) { read() }
