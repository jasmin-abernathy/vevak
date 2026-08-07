/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.sms

import java.text.Normalizer
import java.util.Locale

object SmsCommandParser {
    private val whitespace = Regex("\\s+")

    fun matches(messageBody: String, configuredPhrase: String): Boolean {
        val expected = normalize(configuredPhrase)
        return expected.isNotEmpty() && normalize(messageBody) == expected
    }

    internal fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replace('’', '\'')
            .lowercase(Locale.ROOT)
            .replace(whitespace, " ")
            .trim()
}
