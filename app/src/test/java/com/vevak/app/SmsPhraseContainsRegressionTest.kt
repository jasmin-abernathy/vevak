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

class SmsPhraseContainsRegressionTest {
    @Test
    fun phraseKey_matchesWhenOrdinaryWordsComeBeforeAndAfter() {
        assertTrue(
            SmsCommandParser.matches(
                "Salut, tu peux me dire POSITION MAINTENANT s'il te plaît ?",
                "position maintenant"
            )
        )
    }

    @Test
    fun phraseKey_matchesInTheMiddleWithSmsTypographyNormalization() {
        assertTrue(
            SmsCommandParser.matches(
                "Coucou — OÙ\u202Fest\u00A0l’app ? merci",
                "où est l'app ?"
            )
        )
    }

    @Test
    fun phraseKey_doesNotMatchInsideAnotherWord() {
        assertFalse(SmsCommandParser.matches("J'ai fait un booking pour demain", "ok"))
    }

    @Test
    fun normalRequest_routesWhenPhraseIsContainedInLongerMessage() {
        val settings = VeVakSettings(triggerPhrase = "position maintenant")

        assertEquals(
            IncomingRequestMode.Normal,
            RequestModeResolver.resolve(
                "Salut, POSITION MAINTENANT si tu peux, merci.",
                settings
            )
        )
    }

    @Test
    fun protectedContact_routesToProtectionWhenItsPhraseIsContainedInLongerMessage() {
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
                "Tu peux me dire OÙ ES-TU MAINTENANT s'il te plaît ?",
                protected,
                settings
            )
        )
    }
}
