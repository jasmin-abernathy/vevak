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
    private val nonWordSeparators = Regex("[^\\p{L}\\p{N}]+")

    fun matches(messageBody: String, configuredPhrase: String): Boolean {
        val expectedTokens = tokens(configuredPhrase)
        val messageTokens = tokens(messageBody)
        if (expectedTokens.isEmpty() || expectedTokens.size > messageTokens.size) return false

        return messageTokens.windowed(expectedTokens.size).any { window ->
            window == expectedTokens
        }
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

    /**
     * Keep "contains" semantics without matching a very short key inside another word (for example
     * "ok" inside "booking"). Punctuation and whitespace may naturally surround the configured
     * phrase, so normal conversational SMS still work when the key is in the middle of a sentence.
     */
    private fun tokens(value: String): List<String> = normalize(value)
        .replace(nonWordSeparators, " ")
        .split(' ')
        .filter(String::isNotBlank)
}
