/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.backup

import android.content.Context
import android.net.Uri
import com.vevak.app.model.VeVakSettings
import java.io.ByteArrayOutputStream

class SettingsBackupRepository(private val context: Context) {
    fun export(uri: Uri, settings: VeVakSettings, password: String) {
        val plaintext = SettingsBackupSerializer.serialize(settings)
        val encrypted = EncryptedBackupCodec.encrypt(plaintext, password)
        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
            output.write(encrypted)
            output.flush()
        } ?: error("Unable to open backup destination")
    }

    fun import(uri: Uri, password: String): VeVakSettings {
        val encrypted = context.contentResolver.openInputStream(uri)?.use(::readBounded)
            ?: error("Unable to open backup file")
        val plaintext = EncryptedBackupCodec.decrypt(encrypted, password)
        return SettingsBackupSerializer.deserialize(plaintext).withAllAuthorizationsRevoked()
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= MAX_BACKUP_BYTES) { "Backup file is too large" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 512 * 1024
    }
}
