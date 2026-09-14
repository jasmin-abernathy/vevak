/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.vevak.app.location

import kotlinx.coroutines.CancellationException

/** Missing capabilities may fall back; cancellation must stop the entire resolution. */
internal inline fun <T> locationAttempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Result.failure(failure)
}
