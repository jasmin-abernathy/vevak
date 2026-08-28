/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.model

enum class MapProvider(val label: String) {
    CoMaps("CoMaps / application cartographique"),
    OpenStreetMap("OpenStreetMap"),
    GoogleMaps("Google Maps")
}

enum class AuthorizationDuration(val days: Int, val label: String) {
    OneDay(1, "24 heures"),
    SevenDays(7, "7 jours"),
    ThirtyDays(30, "30 jours");

    fun expiresAt(nowMillis: Long): Long = nowMillis + days * DAY_MILLIS

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}

data class VeVakSettings(
    val completedOnboarding: Boolean = false,
    // Legacy/primary-contact fields are retained for a migration-safe transition from the
    // original single-contact model. Additional contacts use TrustedContact records below.
    val contactName: String = "",
    val contactPhone: String = "",
    val triggerPhrase: String = "Info Mari",
    val additionalTrustedContacts: List<TrustedContact> = emptyList(),
    val includeBattery: Boolean = true,
    val includeAccuracy: Boolean = true,
    val mapProvider: MapProvider = MapProvider.CoMaps,
    val minRequestIntervalSeconds: Int = 900,
    val maxCachedLocationAgeSeconds: Int = 120,
    val locationTimeoutSeconds: Int = 8,
    val allowStaleFallback: Boolean = true,
    // These timestamps belong to the primary contact. Additional contacts carry their own.
    val authorizationGrantedAtEpochMs: Long = 0L,
    val authorizationExpiresAtEpochMs: Long = 0L,
    val duressEnabled: Boolean = false,
    val duressPhrase: String = "",
    val fallbackLatitude: Double? = null,
    val fallbackLongitude: Double? = null,
    val fallbackAccuracyMeters: Float? = null,
    val trustedWifiEnabled: Boolean = false,
    val trustedWifiHash: String = "",
    val trustedPlaceLabel: String = "Maison",
    val discreetModeUntilEpochMs: Long = 0L
) {
    fun primaryTrustedContact(): TrustedContact = TrustedContact(
        id = PRIMARY_CONTACT_ID,
        name = contactName,
        phone = contactPhone,
        triggerPhrase = triggerPhrase,
        authorizationGrantedAtEpochMs = authorizationGrantedAtEpochMs,
        authorizationExpiresAtEpochMs = authorizationExpiresAtEpochMs
    )

    fun trustedContacts(): List<TrustedContact> = buildList {
        val primary = primaryTrustedContact()
        if (primary.isConfigured()) add(primary)
        addAll(additionalTrustedContacts.filter { it.isConfigured() })
    }.take(MAX_TRUSTED_CONTACTS)

    fun activeTrustedContacts(nowMillis: Long = System.currentTimeMillis()): List<TrustedContact> =
        trustedContacts().filter { it.hasActiveAuthorization(nowMillis) }

    fun contactById(id: String): TrustedContact? =
        if (id == PRIMARY_CONTACT_ID) primaryTrustedContact().takeIf { it.isConfigured() }
        else additionalTrustedContacts.firstOrNull { it.id == id && it.isConfigured() }

    fun normalTriggerPhrases(): List<String> = trustedContacts().map { it.triggerPhrase }.filter { it.isNotBlank() }

    fun hasActiveAuthorization(nowMillis: Long = System.currentTimeMillis()): Boolean =
        activeTrustedContacts(nowMillis).isNotEmpty()

    fun latestActiveAuthorizationExpiry(nowMillis: Long = System.currentTimeMillis()): Long? =
        activeTrustedContacts(nowMillis).maxOfOrNull { it.authorizationExpiresAtEpochMs }

    fun withPrimaryContact(contact: TrustedContact): VeVakSettings = copy(
        contactName = contact.name,
        contactPhone = contact.phone,
        triggerPhrase = contact.triggerPhrase,
        authorizationGrantedAtEpochMs = contact.authorizationGrantedAtEpochMs,
        authorizationExpiresAtEpochMs = contact.authorizationExpiresAtEpochMs
    )

    fun withUpdatedContact(contact: TrustedContact): VeVakSettings = when (contact.id) {
        PRIMARY_CONTACT_ID -> withPrimaryContact(contact)
        else -> copy(
            additionalTrustedContacts = additionalTrustedContacts.map {
                if (it.id == contact.id) contact else it
            }
        )
    }

    fun withoutContact(id: String): VeVakSettings = when (id) {
        PRIMARY_CONTACT_ID -> copy(
            contactName = "",
            contactPhone = "",
            triggerPhrase = "Info Mari",
            authorizationGrantedAtEpochMs = 0L,
            authorizationExpiresAtEpochMs = 0L
        )
        else -> copy(additionalTrustedContacts = additionalTrustedContacts.filterNot { it.id == id })
    }

    fun withAllAuthorizationsRevoked(): VeVakSettings = copy(
        authorizationGrantedAtEpochMs = 0L,
        authorizationExpiresAtEpochMs = 0L,
        additionalTrustedContacts = additionalTrustedContacts.map { it.revoke() },
        discreetModeUntilEpochMs = 0L
    )

    fun hasFallbackCoordinates(): Boolean =
        fallbackLatitude?.let { it in -90.0..90.0 } == true &&
            fallbackLongitude?.let { it in -180.0..180.0 } == true

    fun hasValidDuressConfiguration(): Boolean =
        !duressEnabled || (duressPhrase.isNotBlank() && hasFallbackCoordinates())

    fun hasTrustedWifiConfiguration(): Boolean =
        trustedWifiEnabled && trustedWifiHash.isNotBlank() && trustedPlaceLabel.isNotBlank()

    fun isDiscreetModeActive(nowMillis: Long = System.currentTimeMillis()): Boolean =
        discreetModeUntilEpochMs > nowMillis

    fun isConfigured(nowMillis: Long = System.currentTimeMillis()): Boolean =
        completedOnboarding &&
            trustedContacts().any { it.isConfigured() && it.hasActiveAuthorization(nowMillis) } &&
            hasValidDuressConfiguration()

    companion object {
        const val PRIMARY_CONTACT_ID = "primary"
        const val MAX_TRUSTED_CONTACTS = 5
    }
}
