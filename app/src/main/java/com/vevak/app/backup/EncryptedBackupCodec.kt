/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-protected, authenticated VeVak backup container.
 *
 * The password-derived key makes the file portable to another phone. AES-GCM
 * provides confidentiality and integrity; corrupted or wrongly decrypted files
 * fail instead of producing partially trusted settings.
 */
object EncryptedBackupCodec {
    private val MAGIC = byteArrayOf('V'.code.toByte(), 'V'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte())
    private val AAD = "VeVak encrypted backup v1".toByteArray(Charsets.UTF_8)
    private const val FORMAT_VERSION = 1
    private const val DEFAULT_ITERATIONS = 150_000
    private const val MIN_ITERATIONS = 50_000
    private const val MAX_ITERATIONS = 500_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val MAX_FILE_BYTES = 512 * 1024

    fun encrypt(plaintext: ByteArray, password: String): ByteArray {
        require(password.length in 8..256) { "Backup password must contain 8 to 256 characters" }
        require(plaintext.size <= MAX_FILE_BYTES) { "Backup payload is too large" }

        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = deriveKey(password, salt, DEFAULT_ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            updateAAD(AAD)
        }
        val ciphertext = cipher.doFinal(plaintext)

        return ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.write(MAGIC)
                data.writeInt(FORMAT_VERSION)
                data.writeInt(DEFAULT_ITERATIONS)
                data.writeInt(salt.size)
                data.writeInt(iv.size)
                data.writeInt(ciphertext.size)
                data.write(salt)
                data.write(iv)
                data.write(ciphertext)
            }
            output.toByteArray()
        }
    }

    fun decrypt(container: ByteArray, password: String): ByteArray {
        require(password.length in 8..256) { "Invalid backup password" }
        require(container.size in 1..MAX_FILE_BYTES) { "Invalid backup size" }

        val parsed = DataInputStream(ByteArrayInputStream(container)).use { data ->
            val magic = ByteArray(MAGIC.size).also(data::readFully)
            require(magic.contentEquals(MAGIC)) { "Not a VeVak backup" }
            require(data.readInt() == FORMAT_VERSION) { "Unsupported VeVak backup version" }
            val iterations = data.readInt()
            require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Invalid backup KDF parameters" }
            val saltSize = data.readInt()
            val ivSize = data.readInt()
            val ciphertextSize = data.readInt()
            require(saltSize == SALT_BYTES && ivSize == IV_BYTES) { "Invalid backup crypto parameters" }
            require(ciphertextSize in 16..MAX_FILE_BYTES) { "Invalid backup payload size" }
            val expectedRemaining = saltSize + ivSize + ciphertextSize
            require(data.available() == expectedRemaining) { "Truncated or oversized backup" }
            val salt = ByteArray(saltSize).also(data::readFully)
            val iv = ByteArray(ivSize).also(data::readFully)
            val ciphertext = ByteArray(ciphertextSize).also(data::readFully)
            Parsed(iterations, salt, iv, ciphertext)
        }

        val key = deriveKey(password, parsed.salt, parsed.iterations)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, parsed.iv))
            updateAAD(AAD)
            doFinal(parsed.ciphertext)
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val chars = password.toCharArray()
        return try {
            val spec = PBEKeySpec(chars, salt, iterations, KEY_BITS)
            try {
                val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
                SecretKeySpec(encoded, "AES")
            } finally {
                spec.clearPassword()
            }
        } finally {
            chars.fill('\u0000')
        }
    }

    private data class Parsed(
        val iterations: Int,
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray
    )
}
