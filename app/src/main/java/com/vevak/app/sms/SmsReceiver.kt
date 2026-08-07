/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.vevak.app.VeVakApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pendingResult = goAsync()
        val application = context.applicationContext as VeVakApplication
        application.applicationScope.launch(Dispatchers.IO) {
            try {
                SmsRequestHandler(context.applicationContext).handle(intent)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
