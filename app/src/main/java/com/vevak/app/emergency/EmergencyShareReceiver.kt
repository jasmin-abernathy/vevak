/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.emergency

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.vevak.app.data.EmergencyRecipientStore
import com.vevak.app.data.VeVakSettingsRepository
import com.vevak.app.location.VeVakLocationRepository
import com.vevak.app.sms.SmsReplyFormatter
import com.vevak.app.sms.SmsReplySender
import com.vevak.app.system.BatteryReader
import com.vevak.app.system.RequestVisibilityNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Explicit receiver used by the persistent notification's emergency action.
 *
 * There is deliberately no confirmation screen: tapping the notification action is the user's
 * local confirmation. Automatic-request anti-tracking limits do not apply to this voluntary alert.
 */
class EmergencyShareReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND_EMERGENCY_LOCATION) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                sendEmergency(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun sendEmergency(context: Context) {
        val notifier = RequestVisibilityNotifier(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            notifier.showEmergencyResult(0, 0, "Autorisation SMS absente : aucune alerte envoyée.")
            return
        }

        val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId().takeIf { it >= 0 }
        if (subscriptionId == null) {
            notifier.showEmergencyResult(0, 0, "Aucune SIM SMS par défaut : aucune alerte envoyée.")
            return
        }

        val settings = VeVakSettingsRepository(context).current()
        if (!settings.completedOnboarding) {
            notifier.showEmergencyResult(0, 0, "VeVak n'est pas encore configuré.")
            return
        }

        val recipients = EmergencyRecipientStore(context).recipients(settings)
        if (recipients.isEmpty()) {
            notifier.showEmergencyResult(0, 0, "Aucun contact d'urgence actuellement autorisé.")
            return
        }

        val lastKnown = runCatching { VeVakLocationRepository(context).fetchLastKnownLocation() }.getOrNull()
        val batteryLabel = BatteryReader(context).label()
        val body = if (lastKnown != null) {
            SmsReplyFormatter.formatEmergencyShareWithBatteryLabel(settings, lastKnown, batteryLabel)
        } else {
            SmsReplyFormatter.formatEmergencyUnavailableWithBatteryLabel(settings, batteryLabel)
        }

        val sender = SmsReplySender(context)
        var accepted = 0
        recipients.forEach { contact ->
            if (runCatching { sender.send(contact.phone, body, subscriptionId) }.isSuccess) accepted++
        }

        notifier.showEmergencyResult(
            accepted,
            recipients.size,
            if (lastKnown != null) {
                "Dernière position connue ${lastKnown.ageLabel()}."
            } else {
                "Aucune position connue ; l'alerte a indiqué cette indisponibilité."
            }
        )
    }

    companion object {
        const val ACTION_SEND_EMERGENCY_LOCATION = "com.vevak.app.action.SEND_EMERGENCY_LOCATION"
    }
}
