/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.backup

import com.vevak.app.data.TrustedContactStorageCodec
import com.vevak.app.model.MapProvider
import com.vevak.app.model.VeVakSettings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Properties

/**
 * Serialises VeVak configuration for the encrypted backup container.
 *
 * Active authorisations and temporary discreet-mode state are intentionally
 * excluded. Restoring a backup must never silently re-authorise a contact.
 */
object SettingsBackupSerializer {
    private const val FORMAT_VERSION = "1"

    fun serialize(settings: VeVakSettings): ByteArray {
        val safe = settings.withAllAuthorizationsRevoked().copy(discreetModeUntilEpochMs = 0L)
        val properties = Properties().apply {
            setProperty("format", FORMAT_VERSION)
            setProperty("completedOnboarding", safe.completedOnboarding.toString())
            setProperty("contactName", safe.contactName)
            setProperty("contactPhone", safe.contactPhone)
            setProperty("triggerPhrase", safe.triggerPhrase)
            setProperty(
                "additionalTrustedContacts",
                TrustedContactStorageCodec.encode(safe.additionalTrustedContacts.map { it.revoke() })
            )
            setProperty("includeBattery", safe.includeBattery.toString())
            setProperty("includeAccuracy", safe.includeAccuracy.toString())
            setProperty("mapProvider", safe.mapProvider.name)
            setProperty("minRequestIntervalSeconds", safe.minRequestIntervalSeconds.toString())
            setProperty("maxCachedLocationAgeSeconds", safe.maxCachedLocationAgeSeconds.toString())
            setProperty("locationTimeoutSeconds", safe.locationTimeoutSeconds.toString())
            setProperty("allowStaleFallback", safe.allowStaleFallback.toString())
            setProperty("duressEnabled", safe.duressEnabled.toString())
            setProperty("duressPhrase", safe.duressPhrase)
            safe.fallbackLatitude?.let { setProperty("fallbackLatitude", it.toString()) }
            safe.fallbackLongitude?.let { setProperty("fallbackLongitude", it.toString()) }
            safe.fallbackAccuracyMeters?.let { setProperty("fallbackAccuracyMeters", it.toString()) }
            setProperty("trustedWifiEnabled", safe.trustedWifiEnabled.toString())
            setProperty("trustedWifiHash", safe.trustedWifiHash)
            setProperty("trustedPlaceLabel", safe.trustedPlaceLabel)
        }

        return ByteArrayOutputStream().use { output ->
            OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
                properties.store(writer, "VeVak encrypted configuration payload")
            }
            output.toByteArray()
        }
    }

    fun deserialize(bytes: ByteArray): VeVakSettings {
        require(bytes.size <= MAX_PLAINTEXT_BYTES) { "Backup payload is too large" }
        val properties = Properties()
        InputStreamReader(ByteArrayInputStream(bytes), StandardCharsets.UTF_8).use { reader ->
            properties.load(reader)
        }
        require(properties.getProperty("format") == FORMAT_VERSION) { "Unsupported backup format" }

        val provider = runCatching {
            MapProvider.valueOf(properties.getProperty("mapProvider") ?: MapProvider.CoMaps.name)
        }.getOrDefault(MapProvider.CoMaps)

        val restored = VeVakSettings(
            completedOnboarding = properties.boolean("completedOnboarding", true),
            contactName = properties.getProperty("contactName").orEmpty(),
            contactPhone = properties.getProperty("contactPhone").orEmpty(),
            triggerPhrase = properties.getProperty("triggerPhrase")?.takeIf { it.isNotBlank() } ?: "Info Mari",
            additionalTrustedContacts = TrustedContactStorageCodec.decode(
                properties.getProperty("additionalTrustedContacts").orEmpty()
            ).filter { it.id != VeVakSettings.PRIMARY_CONTACT_ID }
                .take(VeVakSettings.MAX_TRUSTED_CONTACTS - 1)
                .map { it.revoke() },
            includeBattery = properties.boolean("includeBattery", true),
            includeAccuracy = properties.boolean("includeAccuracy", true),
            mapProvider = provider,
            minRequestIntervalSeconds = properties.int("minRequestIntervalSeconds", 900).coerceIn(900, 3600),
            maxCachedLocationAgeSeconds = properties.int("maxCachedLocationAgeSeconds", 120).coerceIn(30, 3600),
            locationTimeoutSeconds = properties.int("locationTimeoutSeconds", 8).coerceIn(3, 30),
            allowStaleFallback = properties.boolean("allowStaleFallback", true),
            authorizationGrantedAtEpochMs = 0L,
            authorizationExpiresAtEpochMs = 0L,
            duressEnabled = properties.boolean("duressEnabled", false),
            duressPhrase = properties.getProperty("duressPhrase").orEmpty(),
            fallbackLatitude = properties.getProperty("fallbackLatitude")?.toDoubleOrNull(),
            fallbackLongitude = properties.getProperty("fallbackLongitude")?.toDoubleOrNull(),
            fallbackAccuracyMeters = properties.getProperty("fallbackAccuracyMeters")?.toFloatOrNull(),
            trustedWifiEnabled = properties.boolean("trustedWifiEnabled", false),
            trustedWifiHash = properties.getProperty("trustedWifiHash").orEmpty(),
            trustedPlaceLabel = properties.getProperty("trustedPlaceLabel")?.trim().takeUnless { it.isNullOrBlank() } ?: "Maison",
            discreetModeUntilEpochMs = 0L
        )
        return restored.withAllAuthorizationsRevoked()
    }

    private fun Properties.boolean(key: String, default: Boolean): Boolean =
        getProperty(key)?.toBooleanStrictOrNull() ?: default

    private fun Properties.int(key: String, default: Int): Int =
        getProperty(key)?.toIntOrNull() ?: default

    private const val MAX_PLAINTEXT_BYTES = 256 * 1024
}
