/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vevak.app.security.RequestRatePolicy

private val Context.runtimeDataStore by preferencesDataStore(name = "vevak_runtime")

class RuntimeStateRepository(private val context: Context) {
    private object Keys {
        val LAST_ACCEPTED_REQUEST = longPreferencesKey("last_accepted_request_epoch_ms")
    }

    suspend fun tryAcquire(nowMillis: Long, minimumIntervalMillis: Long): Boolean {
        var acquired = false
        context.runtimeDataStore.edit { prefs ->
            val previous = prefs[Keys.LAST_ACCEPTED_REQUEST] ?: 0L
            if (RequestRatePolicy.isAllowed(previous, nowMillis, minimumIntervalMillis)) {
                prefs[Keys.LAST_ACCEPTED_REQUEST] = nowMillis
                acquired = true
            }
        }
        return acquired
    }

    suspend fun reset() {
        context.runtimeDataStore.edit { it.clear() }
    }
}
