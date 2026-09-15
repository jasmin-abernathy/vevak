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
import com.vevak.app.location.VeVakPositionResolution
import com.vevak.app.location.VeVakPositionResolver
import com.vevak.app.sms.SmsReplyFormatter
import com.vevak.app.sms.SmsReplySender
import com.vevak.app.system.BatteryReader
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Explicit receiver used by the local emergency shortcut.
 *
 * There is deliberately no confirmation screen after the shortcut's cancellable arming delay.
 * Automatic-request anti-tracking limits do not apply to this voluntary alert.
 */
class EmergencyShareReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND_EMERGENCY_LOCATION) return
        val armId = intent.getStringExtra(EXTRA_ARM_ID) ?: return
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (!EmergencyShortcutArmController(appContext).consumeIfArmed(armId)) return@launch
                EmergencyFeedback(appContext).result(armId, "Prise en charge. Préparation du message ; annulation terminée.")
                val result = sendEmergency(appContext)
                EmergencyFeedback(appContext).result(armId, result)
            } catch (cancelled: CancellationException) {
                EmergencyFeedback(appContext).result(armId, "Traitement interrompu. Résultat d’envoi inconnu.")
                throw cancelled
            } catch (_: Exception) {
                EmergencyFeedback(appContext).result(armId, "Impossible de terminer la demande. Livraison non confirmée.")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun sendEmergency(context: Context): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return "Envoi non demandé : autorisation SMS absente. Vérifiez les permissions Android."
        }

        val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId().takeIf { it >= 0 }
        if (subscriptionId == null) {
            return "Envoi non demandé : aucune SIM SMS par défaut. Vérifiez les réglages Android."
        }

        val settings = VeVakSettingsRepository(context).current()
        if (!settings.completedOnboarding) {
            return "Envoi non demandé : terminez l'assistant VeVak."
        }

        val recipients = EmergencyRecipientStore(context).recipients(settings)
        if (recipients.isEmpty()) {
            return "Envoi non demandé : aucun destinataire autorisé. Vérifiez Sécurité."
        }

        // Emergency uses the same canonical resolver as an authorised phrase-key request. This keeps
        // trusted-place, current Android position, optional network approximation and remembered
        // fallback semantics consistent instead of maintaining a second location policy.
        val resolution = try {
            VeVakPositionResolver(context).resolve(settings)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            VeVakPositionResolution.Unavailable
        }

        val batteryLabel = BatteryReader(context).label()
        val body = SmsReplyFormatter.formatEmergencyResolutionWithBatteryLabel(
            settings = settings,
            resolution = resolution,
            batteryLabel = batteryLabel
        )

        val sender = SmsReplySender(context)
        val accepted = recipients.count { contact ->
            runCatching { sender.send(contact.phone, body, subscriptionId) }.isSuccess
        }

        return if (accepted == recipients.size) {
            "Demandes remises à Android. Livraison des SMS non confirmée."
        } else {
            "${accepted}/${recipients.size} demandes remises à Android. Certaines tentatives ont échoué ; livraison non confirmée."
        }
    }

    companion object {
        const val ACTION_SEND_EMERGENCY_LOCATION = "com.vevak.app.action.SEND_EMERGENCY_LOCATION"
        const val EXTRA_ARM_ID = "emergency_arm_id"
    }
}
