/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.security

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Local password gate for the deliberately discreet additional-settings area.
 *
 * The password is never stored. Only a salted PBKDF2 verifier is kept in app-private storage,
 * which is excluded from Android backup by the application manifest.
 */
class PrivateSettingsAccessRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    // A malformed existing verifier must not reopen password creation.
    fun hasPassword(): Boolean = preferences.contains(KEY_VERIFIER)

    fun setPassword(password: String): Boolean {
        if (!PrivateSettingsPassword.isAcceptable(password)) return false
        return preferences.edit()
            .putString(KEY_VERIFIER, PrivateSettingsPassword.createVerifier(password))
            .commit()
    }

    fun verify(password: String): Boolean {
        val verifier = preferences.getString(KEY_VERIFIER, null) ?: return false
        return PrivateSettingsPassword.verify(password, verifier)
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "vevak_private_settings_access"
        const val KEY_VERIFIER = "password_verifier_v1"
    }
}

internal object PrivateSettingsPassword {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 128
    private const val VERSION = "v1"
    private const val ITERATIONS = 210_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun isAcceptable(password: String): Boolean = password.length in MIN_LENGTH..MAX_LENGTH

    fun createVerifier(password: String): String {
        require(isAcceptable(password))
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val derived = derive(password, salt)
        return listOf(
            VERSION,
            ITERATIONS.toString(),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(derived)
        ).joinToString("$")
    }

    fun isEncodedVerifier(encoded: String): Boolean = parse(encoded) != null

    fun verify(password: String, encoded: String): Boolean {
        if (password.length > MAX_LENGTH) return false
        val parsed = parse(encoded) ?: return false
        val candidate = derive(password, parsed.salt)
        return MessageDigest.isEqual(candidate, parsed.expected)
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            chars.fill('\u0000')
            spec.clearPassword()
        }
    }

    private fun parse(encoded: String): ParsedVerifier? {
        val parts = encoded.split('$')
        if (parts.size != 4 || parts[0] != VERSION || parts[1].toIntOrNull() != ITERATIONS) {
            return null
        }
        return runCatching {
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            if (salt.size != SALT_BYTES || expected.size != KEY_LENGTH_BITS / 8) return null
            ParsedVerifier(salt, expected)
        }.getOrNull()
    }

    private data class ParsedVerifier(val salt: ByteArray, val expected: ByteArray)
}
