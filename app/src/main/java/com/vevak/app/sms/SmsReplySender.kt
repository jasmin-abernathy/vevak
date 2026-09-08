/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.sms

import android.content.Context
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

class SmsReplySender(private val context: Context) {
    @Suppress("DEPRECATION")
    fun send(destination: String, body: String, subscriptionId: Int?) {
        val normalizedDestination = PhoneNumberUtils.normalizeNumber(destination)
            .takeIf { it.isNotBlank() }
            ?: destination.trim()
        require(normalizedDestination.isNotBlank()) { "SMS destination is blank" }

        val baseManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else {
            SmsManager.getDefault()
        }
        val resolvedSubscriptionId = SmsSubscriptionPolicy.resolve(
            receivedSubscriptionId = subscriptionId,
            defaultSubscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
        ) ?: error("No deterministic SMS subscription is available")
        val manager: SmsManager = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> baseManager.createForSubscriptionId(resolvedSubscriptionId)
            else -> SmsManager.getSmsManagerForSubscriptionId(resolvedSubscriptionId)
        }

        val parts = manager.divideMessage(body)
        if (parts.size <= 1) {
            manager.sendTextMessage(normalizedDestination, null, body, null, null)
        } else {
            manager.sendMultipartTextMessage(normalizedDestination, null, parts, null, null)
        }
    }
}

internal object SmsSubscriptionPolicy {
    fun resolve(receivedSubscriptionId: Int?, defaultSubscriptionId: Int): Int? =
        receivedSubscriptionId
            ?.takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID && it >= 0 }
            ?: defaultSubscriptionId.takeIf {
                it != SubscriptionManager.INVALID_SUBSCRIPTION_ID && it >= 0
            }
}
