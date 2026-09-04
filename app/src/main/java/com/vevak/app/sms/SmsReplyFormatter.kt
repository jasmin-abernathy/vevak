/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.sms

import com.vevak.app.location.VeVakLocationSnapshot
import com.vevak.app.model.VeVakSettings

object SmsReplyFormatter {
    private const val VERY_COARSE_NETWORK_RADIUS_METERS = 10_000f

    fun format(
        settings: VeVakSettings,
        location: VeVakLocationSnapshot?,
        batteryPercent: Int?
    ): String = formatWithBatteryLabel(
        settings,
        location,
        batteryPercent?.let { "Batterie : $it %" }
    )

    fun formatWithBatteryLabel(
        settings: VeVakSettings,
        location: VeVakLocationSnapshot?,
        batteryLabel: String?
    ): String = buildString {
        append("VeVak")
        when {
            location == null -> append("\nPosition indisponible.")
            location.isApproximateNetworkEstimate() -> appendNetworkEstimate(settings, location)
            else -> appendRealLocation(settings, location)
        }
        appendBattery(settings.includeBattery, batteryLabel)
    }

    fun formatManualShare(
        settings: VeVakSettings,
        location: VeVakLocationSnapshot,
        batteryPercent: Int?
    ): String = format(settings, location, batteryPercent)

    fun formatManualShareWithBatteryLabel(
        settings: VeVakSettings,
        location: VeVakLocationSnapshot,
        batteryLabel: String?
    ): String = formatWithBatteryLabel(settings, location, batteryLabel)

    fun formatEmergencyShareWithBatteryLabel(
        settings: VeVakSettings,
        location: VeVakLocationSnapshot,
        batteryLabel: String?
    ): String = buildString {
        append("URGENCE VeVak")
        appendRealLocation(settings, location)
        appendBattery(settings.includeBattery, batteryLabel)
    }

    fun formatEmergencyUnavailableWithBatteryLabel(
        settings: VeVakSettings,
        batteryLabel: String?
    ): String = buildString {
        append("URGENCE VeVak")
        append("\nAucune dernière position connue n'est disponible sur le téléphone.")
        appendBattery(settings.includeBattery, batteryLabel)
    }

    fun formatTrustedPlace(label: String = "Maison"): String = trustedPlaceText(label)

    fun formatTrustedPlaceWithBattery(
        label: String,
        batteryLabel: String?,
        includeBattery: Boolean
    ): String = buildString {
        append(trustedPlaceText(label))
        appendBattery(includeBattery, batteryLabel)
    }

    fun formatManualTrustedPlace(label: String): String = trustedPlaceText(label)

    fun formatManualTrustedPlaceWithBattery(
        label: String,
        batteryLabel: String?,
        includeBattery: Boolean
    ): String = formatTrustedPlaceWithBattery(label, batteryLabel, includeBattery)

    private fun StringBuilder.appendRealLocation(settings: VeVakSettings, location: VeVakLocationSnapshot) {
        append("\nDernière position connue : ")
        append(location.ageLabel())
        append('\n')
        append(MapLinkBuilder.build(settings.mapProvider, location.latitude, location.longitude))
        if (settings.includeAccuracy) {
            append("\nRayon approximatif : ")
            append(location.accuracyLabel())
        }
    }

    private fun StringBuilder.appendNetworkEstimate(settings: VeVakSettings, location: VeVakLocationSnapshot) {
        val accuracy = location.accuracyMeters
        append("\nDernière zone connue : ")
        append(location.ageLabel())
        append("\nEstimation via le réseau — pas une position exacte.")
        if (settings.includeAccuracy) {
            if (accuracy != null && accuracy > VERY_COARSE_NETWORK_RADIUS_METERS) {
                append("\nPrécision faible : la zone peut s'étendre sur ")
                append(location.accuracyLabel())
                append(" autour du centre estimé.")
            } else {
                append("\nRayon approximatif : ")
                append(location.accuracyLabel())
            }
        }
        append("\nCarte de la zone : ")
        append(
            MapLinkBuilder.buildApproximateZone(
                settings.mapProvider,
                location.latitude,
                location.longitude,
                accuracy
            )
        )
    }

    private fun trustedPlaceText(label: String): String {
        val cleanLabel = label.trim().ifBlank { "Maison" }
        return if (cleanLabel.equals("Maison", ignoreCase = true)) {
            "Je suis chez moi"
        } else {
            "Je suis à : $cleanLabel"
        }
    }

    private fun StringBuilder.appendBattery(includeBattery: Boolean, batteryLabel: String?) {
        if (includeBattery && !batteryLabel.isNullOrBlank()) {
            append('\n')
            append(batteryLabel.trim())
        }
    }
}
