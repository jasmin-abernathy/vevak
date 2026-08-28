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

    fun formatTrustedPlace(
        settings: VeVakSettings,
        label: String,
        batteryPercent: Int?
    ): String = buildString {
        append("VeVak")
        append("\nLe telephone est a ")
        append(label.trim().ifBlank { "Maison" })
        append('.')
        appendBattery(settings, batteryPercent)
    }

    private fun StringBuilder.appendBattery(settings: VeVakSettings, batteryPercent: Int?) {
        if (settings.includeBattery && batteryPercent != null) {
            append("\nBatterie: ")
            append(batteryPercent)
            append(" %")
        }
    }
}