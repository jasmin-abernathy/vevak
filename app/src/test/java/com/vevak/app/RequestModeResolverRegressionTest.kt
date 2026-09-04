/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app

import com.vevak.app.model.TrustedContact
import com.vevak.app.model.VeVakSettings
import com.vevak.app.security.IncomingRequestMode
import com.vevak.app.security.RequestModeResolver
import com.vevak.app.sms.SmsCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun normalPhrase_routesWhenContainedInsideConversationalSms() {
        val settings = VeVakSettings(triggerPhrase = "position maintenant")

        assertEquals(
            IncomingRequestMode.Normal,
            RequestModeResolver.resolve(
                "Salut, tu peux me donner POSITION MAINTENANT s'il te plaît ?",
                settings
            )
        )
    }

    @Test
    fun phraseContains_keepsWordBoundariesForShortKeys() {
        assertTrue(SmsCommandParser.matches("ok, je confirme", "ok"))
        assertFalse(SmsCommandParser.matches("booking confirmé", "ok"))
    }

    @Test
    fun phraseContains_normalizesNbspAndTypographicApostropheInsideLongerSms() {
        assertTrue(
            SmsCommandParser.matches(
                "Salut — peux-tu m'envoyer où\u00A0es-tu ? merci",
                "OÙ ES-TU ?"
            )
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

    @Test
    fun protectedContact_routesDuressWhenItsExistingPhraseIsInsideLongerSms() {
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
            RequestModeResolver.resolve(
                "Coucou, OÙ ES-TU MAINTENANT stp ?",
                protected,
                settings
            )
        )
    }
}
