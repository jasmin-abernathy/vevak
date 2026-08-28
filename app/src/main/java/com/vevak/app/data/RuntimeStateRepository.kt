/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vevak.app.security.RequestRatePolicy
import com.vevak.app.security.RequestRateState

private val Context.runtimeDataStore by preferencesDataStore(name = "vevak_runtime")

class RuntimeStateRepository(private val context: Context) {
    private object Keys {
        val LAST_ACCEPTED_REQUEST = longPreferencesKey("last_accepted_request_epoch_ms")
        val WINDOW_START = longPreferencesKey("request_window_start_epoch_ms")
        val WINDOW_COUNT = intPreferencesKey("request_window_count")
    }

    suspend fun tryAcquire(nowMillis: Long, minimumIntervalMillis: Long): Boolean {
        var acquired = false
        context.runtimeDataStore.edit { prefs ->
            val current = RequestRateState(
                lastAcceptedMillis = prefs[Keys.LAST_ACCEPTED_REQUEST] ?: 0L,
                windowStartMillis = prefs[Keys.WINDOW_START] ?: 0L,
                acceptedInWindow = prefs[Keys.WINDOW_COUNT] ?: 0
            )
            val evaluation = RequestRatePolicy.evaluate(current, nowMillis, minimumIntervalMillis)
            if (evaluation.allowed) {
                prefs[Keys.LAST_ACCEPTED_REQUEST] = evaluation.state.lastAcceptedMillis
                prefs[Keys.WINDOW_START] = evaluation.state.windowStartMillis
                prefs[Keys.WINDOW_COUNT] = evaluation.state.acceptedInWindow
                acquired = true
            }
        }
        return acquired
    }

    suspend fun reset() {
        context.runtimeDataStore.edit { it.clear() }
    }
}
