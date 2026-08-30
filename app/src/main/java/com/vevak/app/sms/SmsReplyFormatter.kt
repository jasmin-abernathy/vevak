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
    ): String = buildString {
        append("VeVak")
        if (location == null) {
            append("\nPosition indisponible.")
        } else {
            appendLocationKind(location)
            appendAddress(location)
            append('\n')
            append(MapLinkBuilder.build(settings.mapProvider, location.latitude, location.longitude))
            append("\nPosition: ")
            append(location.ageLabel())
            if (settings.includeAccuracy) {
                append("\nPrecision: ")
                append(location.accuracyLabel())
            }
        }
        appendBattery(settings, batteryPercent)
    }

    fun formatManualShare(
        settings: VeVakSettings,
        location: VeVakLocationSnapshot,
        batteryPercent: Int?
    ): String = buildString {
        append("VeVak")
        append("\nJe partage ma position :")
        appendLocationKind(location)
        appendAddress(location)
        append('\n')
        append(MapLinkBuilder.build(settings.mapProvider, location.latitude, location.longitude))
        append("\nPosition: ")
        append(location.ageLabel())
        if (settings.includeAccuracy) {
            append("\nPrecision: ")
            append(location.accuracyLabel())
        }
        appendBattery(settings, batteryPercent)
    }

    fun formatTrustedPlace(label: String = "Maison"): String =
        if (label.equals("Maison", ignoreCase = true)) "Je suis à la maison" else "Je suis à : $label"

    fun formatManualTrustedPlace(label: String): String =
        "VeVak\nJe partage mon lieu reconnu : ${label.trim().ifBlank { "Maison" }}"

    private fun StringBuilder.appendLocationKind(location: VeVakLocationSnapshot) {
        if (location.isApproximateNetworkEstimate()) {
            append("\nPosition approximative via le réseau (adresse IP), pas une position GPS.")
        }
    }

    private fun StringBuilder.appendAddress(location: VeVakLocationSnapshot) {
        location.address?.trim()?.takeIf { it.isNotBlank() }?.let {
            append("\nAdresse approx. : ")
            append(it)
        }
    }

    private fun StringBuilder.appendBattery(settings: VeVakSettings, batteryPercent: Int?) {
        if (settings.includeBattery && batteryPercent != null) {
            append("\nBatterie: ")
            append(batteryPercent)
            append(" %")
        }
    }
}
