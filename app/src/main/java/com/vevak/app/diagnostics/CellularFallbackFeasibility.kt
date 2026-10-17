/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.diagnostics

/**
 * Redacted metadata used only to decide whether a future offline cell database is worth testing.
 * Raw MCC/MNC/area/cell identifiers never enter this model.
 */
internal data class CellObservationMetadata(
    val radio: String,
    val registered: Boolean,
    val lookupIdentityComplete: Boolean,
    val ageMillis: Long?
)

internal data class CellularFallbackFeasibility(
    val visibleCount: Int,
    val registeredCount: Int,
    val lookupReadyCount: Int,
    val radios: List<String>,
    val freshestAgeMillis: Long?
)

internal object CellularFallbackFeasibilityPolicy {
    fun summarize(observations: List<CellObservationMetadata>): CellularFallbackFeasibility {
        val validAges = observations.mapNotNull { it.ageMillis }.filter { it >= 0L }
        return CellularFallbackFeasibility(
            visibleCount = observations.size,
            registeredCount = observations.count { it.registered },
            lookupReadyCount = observations.count { it.lookupIdentityComplete },
            radios = observations.map { it.radio }.filter { it.isNotBlank() }.distinct().sorted(),
            freshestAgeMillis = validAges.minOrNull()
        )
    }

    fun freshnessLabel(ageMillis: Long?): String = when {
        ageMillis == null -> "ancienneté inconnue"
        ageMillis < 60_000L -> "moins d'une minute"
        ageMillis < 5 * 60_000L -> "moins de 5 minutes"
        ageMillis < 30 * 60_000L -> "moins de 30 minutes"
        else -> "plus de 30 minutes"
    }
}
