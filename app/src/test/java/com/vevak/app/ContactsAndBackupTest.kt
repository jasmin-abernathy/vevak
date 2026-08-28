/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app

import com.vevak.app.backup.EncryptedBackupCodec
import com.vevak.app.backup.SettingsBackupSerializer
import com.vevak.app.data.TrustedContactStorageCodec
import com.vevak.app.model.TrustedContact
import com.vevak.app.model.VeVakSettings
import com.vevak.app.security.DuressPolicy
import com.vevak.app.security.IncomingRequestMode
import com.vevak.app.security.RequestModeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsAndBackupTest {
    @Test
    fun trustedContactCodec_roundTripsUnicodeAndSeparators() {
        val contact = TrustedContact(
            id = "contact-1",
            name = "Camille | 🥕",
            phone = "+33 6 12 34 56 78",
            triggerPhrase = "où es-tu ?\nmerci",
            authorizationGrantedAtEpochMs = 1_000L,
            authorizationExpiresAtEpochMs = 2_000L
        )
        assertEquals(listOf(contact), TrustedContactStorageCodec.decode(TrustedContactStorageCodec.encode(listOf(contact))))
    }

    @Test
    fun authorisation_isEvaluatedPerContact() {
        val now = 10_000L
        val settings = VeVakSettings(
            completedOnboarding = true,
            contactPhone = "+33111111111",
            triggerPhrase = "primaire",
            authorizationGrantedAtEpochMs = 1_000L,
            authorizationExpiresAtEpochMs = 5_000L,
            additionalTrustedContacts = listOf(
                TrustedContact(
                    id = "second",
                    phone = "+33222222222",
                    triggerPhrase = "secondaire",
                    authorizationGrantedAtEpochMs = 9_000L,
                    authorizationExpiresAtEpochMs = 20_000L
                )
            )
        )

        assertFalse(settings.primaryTrustedContact().hasActiveAuthorization(now))
        assertTrue(settings.contactById("second")!!.hasActiveAuthorization(now))
        assertTrue(settings.hasActiveAuthorization(now))
        assertEquals(1, settings.activeTrustedContacts(now).size)
    }

    @Test
    fun requestModeResolver_usesSelectedContactsNormalPhrase() {
        val settings = VeVakSettings(
            contactPhone = "+33111111111",
            triggerPhrase = "phrase principale",
            additionalTrustedContacts = listOf(
                TrustedContact(id = "second", phone = "+33222222222", triggerPhrase = "phrase secondaire")
            )
        )
        val second = settings.contactById("second")!!
        assertEquals(
            IncomingRequestMode.Normal,
            RequestModeResolver.resolve("phrase secondaire", second.triggerPhrase, settings)
        )
        assertEquals(null, RequestModeResolver.resolve("phrase principale", second.triggerPhrase, settings))
    }

    @Test
    fun duressPhrase_mustBeDistinctFromEveryNormalContactPhrase() {
        val settings = VeVakSettings(
            contactPhone = "+33111111111",
            triggerPhrase = "besoin position",
            additionalTrustedContacts = listOf(
                TrustedContact(id = "second", phone = "+33222222222", triggerPhrase = "rappelle moi demain")
            ),
            duressEnabled = true,
            duressPhrase = "rappelle moi demain stp",
            fallbackLatitude = 48.0,
            fallbackLongitude = 2.0
        )
        assertFalse(DuressPolicy.configurationIsValid(settings))
    }

    @Test
    fun encryptedBackup_roundTripsButRevokesAllAuthorisations() {
        val original = VeVakSettings(
            completedOnboarding = true,
            contactName = "Alice",
            contactPhone = "+33111111111",
            triggerPhrase = "besoin position",
            authorizationGrantedAtEpochMs = 1_000L,
            authorizationExpiresAtEpochMs = 99_000L,
            additionalTrustedContacts = listOf(
                TrustedContact(
                    id = "second",
                    name = "Bob",
                    phone = "+33222222222",
                    triggerPhrase = "où es-tu bob",
                    authorizationGrantedAtEpochMs = 2_000L,
                    authorizationExpiresAtEpochMs = 88_000L
                )
            ),
            trustedWifiEnabled = true,
            trustedWifiHash = "deadbeef",
            trustedPlaceLabel = "Maison",
            discreetModeUntilEpochMs = 77_000L
        )
        val password = "correct horse battery staple"
        val plaintext = SettingsBackupSerializer.serialize(original)
        val encrypted = EncryptedBackupCodec.encrypt(plaintext, password)
        assertNotEquals(String(plaintext), String(encrypted))

        val restored = SettingsBackupSerializer.deserialize(EncryptedBackupCodec.decrypt(encrypted, password))
        assertEquals(original.contactPhone, restored.contactPhone)
        assertEquals(original.additionalTrustedContacts.first().phone, restored.additionalTrustedContacts.first().phone)
        assertEquals("deadbeef", restored.trustedWifiHash)
        assertFalse(restored.primaryTrustedContact().hasActiveAuthorization(10_000L))
        assertFalse(restored.additionalTrustedContacts.first().hasActiveAuthorization(10_000L))
        assertEquals(0L, restored.discreetModeUntilEpochMs)
    }

    @Test(expected = Exception::class)
    fun encryptedBackup_rejectsWrongPassword() {
        val encrypted = EncryptedBackupCodec.encrypt("payload".toByteArray(), "password-123")
        EncryptedBackupCodec.decrypt(encrypted, "password-456")
    }
}
