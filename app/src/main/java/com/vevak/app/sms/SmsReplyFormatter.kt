/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.sms

import com.vevak.app.location.VeVakLocationSnapshot
import com.vevak.app.model.VeVakSettings

object SmsReplyFormatter {
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
        if (location == null) {
            append("\nPosition indisponible.")
        } else {
            append("\nDernière position connue")
            if (location.isApproximateNetworkEstimate()) append(" (estimation réseau)")
            append(" : ")
            append(location.ageLabel())
            append('\n')
            append(MapLinkBuilder.build(settings.mapProvider, location.latitude, location.longitude))
            append("\nRayon approximatif : ")
            append(location.accuracyLabel())
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
