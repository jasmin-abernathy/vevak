/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.sms

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

class SmsReplySender(private val context: Context) {
    @Suppress("DEPRECATION")
    fun send(destination: String, body: String, subscriptionId: Int?) {
        val baseManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else {
            SmsManager.getDefault()
        }
        val manager: SmsManager = when {
            subscriptionId == null || subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID -> baseManager
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> baseManager.createForSubscriptionId(subscriptionId)
            else -> SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }

        val parts = manager.divideMessage(body)
        if (parts.size <= 1) {
            manager.sendTextMessage(destination, null, body, null, null)
        } else {
            manager.sendMultipartTextMessage(destination, null, parts, null, null)
        }
    }
}
