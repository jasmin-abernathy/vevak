/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.model

/**
 * One locally configured contact allowed to request a VeVak response.
 *
 * Authorisation belongs to the contact, not to the whole app. This avoids
 * granting or revoking every contact at once when several contacts are used.
 */
data class TrustedContact(
    val id: String,
    val name: String = "",
    val phone: String = "",
    val triggerPhrase: String = "Info Mari",
    val authorizationGrantedAtEpochMs: Long = 0L,
    val authorizationExpiresAtEpochMs: Long = 0L
) {
    fun hasActiveAuthorization(nowMillis: Long = System.currentTimeMillis()): Boolean =
        authorizationGrantedAtEpochMs > 0L &&
            authorizationExpiresAtEpochMs > authorizationGrantedAtEpochMs &&
            nowMillis in authorizationGrantedAtEpochMs until authorizationExpiresAtEpochMs

    fun isConfigured(): Boolean = phone.isNotBlank() && triggerPhrase.isNotBlank()

    fun displayLabel(): String = name.trim().ifBlank { phone.trim() }

    fun revoke(): TrustedContact = copy(
        authorizationGrantedAtEpochMs = 0L,
        authorizationExpiresAtEpochMs = 0L
    )
}
