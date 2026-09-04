/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app

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
    fun duressPhrase_routesIgnoringCapitalizationWithoutFallingThrough() {
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
}
