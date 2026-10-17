/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateSettingsPasswordTest {
    @Test
    fun verifierAcceptsOnlyThePasswordThatCreatedIt() {
        val verifier = PrivateSettingsPassword.createVerifier("phrase secrète")

        assertTrue(PrivateSettingsPassword.verify("phrase secrète", verifier))
        assertFalse(PrivateSettingsPassword.verify("phrase secrete", verifier))
        assertFalse(PrivateSettingsPassword.verify("", verifier))
    }

    @Test
    fun equalPasswordsReceiveDifferentSalts() {
        val first = PrivateSettingsPassword.createVerifier("phrase secrète")
        val second = PrivateSettingsPassword.createVerifier("phrase secrète")

        assertNotEquals(first, second)
        assertTrue(PrivateSettingsPassword.verify("phrase secrète", first))
        assertTrue(PrivateSettingsPassword.verify("phrase secrète", second))
    }

    @Test
    fun malformedOrWeakenedVerifierIsRejected() {
        val verifier = PrivateSettingsPassword.createVerifier("phrase secrète")

        assertFalse(PrivateSettingsPassword.isEncodedVerifier("v1" + "$" + "1" + "$" + "bad" + "$" + "bad"))
        assertFalse(PrivateSettingsPassword.verify("phrase secrète", verifier.replace("210000", "1")))
    }

    @Test
    fun passwordLengthIsBounded() {
        assertFalse(PrivateSettingsPassword.isAcceptable("1234567"))
        assertTrue(PrivateSettingsPassword.isAcceptable("12345678"))
        assertFalse(PrivateSettingsPassword.isAcceptable("x".repeat(129)))
    }
}
