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

    /** Manual share intentionally uses the same factual payload as an automatic normal reply. */
    fun formatManualShare(
        settings: VeVakSettings,
        location: VeVakLocationSnapshot,
        batteryLabel: String?
    ): String = format(settings, location, batteryLabel)

    fun formatTrustedPlace(
        label: String = "Maison",
        batteryLabel: String? = null,
        includeBattery: Boolean = true
    ): String = buildString {
        val cleanLabel = label.trim().ifBlank { "Maison" }
        if (cleanLabel.equals("Maison", ignoreCase = true)) {
            append("Je suis chez moi")
        } else {
            append("Je suis à : ")
            append(cleanLabel)
        }
        appendBattery(includeBattery, batteryLabel)
    }

    fun formatManualTrustedPlace(
        label: String,
        batteryLabel: String? = null,
        includeBattery: Boolean = true
    ): String = formatTrustedPlace(label, batteryLabel, includeBattery)

    private fun StringBuilder.appendBattery(includeBattery: Boolean, batteryLabel: String?) {
        if (includeBattery && !batteryLabel.isNullOrBlank()) {
            append('\n')
            append(batteryLabel.trim())
        }
    }
}
