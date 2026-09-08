/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app

import android.telephony.SubscriptionManager
import com.vevak.app.sms.SmsSubscriptionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsSubscriptionPolicyTest {
    @Test
    fun receivingSimAlwaysWins() {
        assertEquals(7, SmsSubscriptionPolicy.resolve(receivedSubscriptionId = 7, defaultSubscriptionId = 3))
    }

    @Test
    fun defaultSimIsUsedOnlyWhenReceivingSimIsUnavailable() {
        assertEquals(3, SmsSubscriptionPolicy.resolve(receivedSubscriptionId = null, defaultSubscriptionId = 3))
    }

    @Test
    fun ambiguityFailsClosed() {
        assertNull(
            SmsSubscriptionPolicy.resolve(
                receivedSubscriptionId = null,
                defaultSubscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID
            )
        )
    }
}
