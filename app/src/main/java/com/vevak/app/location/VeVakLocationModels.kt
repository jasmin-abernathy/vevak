/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.location

import android.location.Location
import android.os.SystemClock
import kotlin.math.max
import kotlin.math.roundToInt

@Suppress("EnumEntryName")
enum class LocationSource(val label: String) {
    FusedCurrent("Fused / actuelle"),
    FusedLastKnown("Fused / cache"),
    AndroidCurrent("Android / actuelle"),
    AndroidLastKnown("Android / cache"),
    SafetyFallback("Repli de sécurité")
}

data class LocationBackendStatus(
    val name: String,
    val available: Boolean,
    val detail: String
)

data class LocationRequestPolicy(
    val maxAcceptedCacheAgeMillis: Long = 120_000L,
    val currentLocationTimeoutMillis: Long = 8_000L,
    val allowStaleFallback: Boolean = true
)

data class VeVakLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val source: LocationSource,
    val ageMillis: Long,
    val isMocked: Boolean,
    val address: String? = null
) {
    fun ageLabel(): String {
        val seconds = max(0L, ageMillis) / 1000L
        return when {
            seconds < 10 -> "maintenant"
            seconds < 60 -> "il y a ${seconds}s"
            seconds < 3600 -> "il y a ${seconds / 60} min"
            else -> "il y a ${seconds / 3600} h"
        }
    }

    fun accuracyLabel(): String = accuracyMeters?.let { "env. ${it.roundToInt()} m" } ?: "inconnue"
    fun isFresh(maxAgeMillis: Long): Boolean = ageMillis <= maxAgeMillis
}

fun Location.toVeVakSnapshot(source: LocationSource): VeVakLocationSnapshot {
    val age = if (elapsedRealtimeNanos > 0L) {
        (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos).coerceAtLeast(0L) / 1_000_000L
    } else {
        (System.currentTimeMillis() - time).coerceAtLeast(0L)
    }
    return VeVakLocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        source = source,
        ageMillis = age,
        isMocked = isFromMockProvider
    )
}
