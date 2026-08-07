/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.sms

import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager

object SmsIntentReader {
    fun read(intent: Intent): IncomingSms? {
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isEmpty()) return null
        val senders = parts.mapNotNull { it.originatingAddress }.distinct()
        if (senders.size != 1) return null
        val sender = senders.single()
        val body = parts.joinToString(separator = "") { it.messageBody.orEmpty() }
        if (body.isBlank()) return null
        return IncomingSms(sender, body, subscriptionId(intent))
    }

    private fun subscriptionId(intent: Intent): Int? {
        val keys = listOf(
            "subscription",
            "subscription_id",
            "android.telephony.extra.SUBSCRIPTION_INDEX"
        )
        val value = keys.firstNotNullOfOrNull { key ->
            if (intent.hasExtra(key)) intent.getIntExtra(key, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            else null
        }
        return value?.takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
    }
}
