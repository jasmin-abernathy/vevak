/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.sms

import java.text.Normalizer
import java.util.Locale

object SmsCommandParser {
    // SMS keyboards and gateways may introduce non-breaking spaces. Treat them exactly like normal
    // whitespace so the user's phrase is compared by meaning rather than keyboard typography.
    private val whitespace = Regex("[\\s\\u00A0\\u202F]+")

    fun matches(messageBody: String, configuredPhrase: String): Boolean {
        val expected = normalize(configuredPhrase)
        return expected.isNotEmpty() && normalize(messageBody).contains(expected)
    }

    /**
     * Phrase matching is deliberately case-insensitive and locale-independent. NFKC also folds many
     * compatibility characters, while common typographic apostrophes are normalised because SMS
     * keyboards frequently substitute them automatically.
     *
     * The configured phrase may appear anywhere in the SMS: contacts do not need to send a message
     * containing only the phrase-key, so ordinary text can be placed before or after it.
     */
    internal fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('ʼ', '\'')
            .lowercase(Locale.ROOT)
            .replace(whitespace, " ")
            .trim()
}
