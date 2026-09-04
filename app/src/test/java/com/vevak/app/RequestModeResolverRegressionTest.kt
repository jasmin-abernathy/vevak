/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app

import com.vevak.app.model.TrustedContact
import com.vevak.app.model.VeVakSettings
import com.vevak.app.security.IncomingRequestMode
import com.vevak.app.security.RequestModeResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestModeResolverRegressionTest {
    @Test
    fun normalPhrase_routesIgnoringCapitalization() {
        val settings = VeVakSettings(triggerPhrase = "position maintenant")

        assertEquals(
            IncomingRequestMode.Normal,
            RequestModeResolver.resolve("POSITION MAINTENANT", settings)
        )
    }

    @Test
    fun legacyDuressPhrase_routesIgnoringCapitalizationWithoutFallingThrough() {
        val settings = VeVakSettings(
            triggerPhrase = "position maintenant",
            duressEnabled = true,
            duressPhrase = "appelle-moi tout de suite"
        )

        assertEquals(
            IncomingRequestMode.Duress,
            RequestModeResolver.resolve("APPELLE-MOI TOUT DE SUITE", settings)
        )
    }

    @Test
    fun protectedContact_usesItsExistingPhraseIgnoringCapitalization() {
        val protected = TrustedContact(
            id = "protected",
            phone = "+33600000000",
            triggerPhrase = "où es-tu maintenant"
        )
        val settings = VeVakSettings(
            triggerPhrase = "phrase principale",
            additionalTrustedContacts = listOf(protected),
            duressEnabled = true,
            protectedContactId = protected.id,
            fallbackLatitude = 48.0,
            fallbackLongitude = 2.0
        )

        assertEquals(
            IncomingRequestMode.Duress,
            RequestModeResolver.resolve("OÙ ES-TU MAINTENANT", protected, settings)
        )
    }
}
