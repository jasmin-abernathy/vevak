/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.location

import android.location.Location

interface LocationProvider {
    val currentSource: LocationSource
    val lastKnownSource: LocationSource

    suspend fun lastKnownLocation(): Location?
    suspend fun currentLocation(timeoutMillis: Long): Location?
    fun backendStatus(): LocationBackendStatus
}
