/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.vevak.app.MainActivity
import com.vevak.app.R

/**
 * Notification support is optional. Automatic phrase-key replies must never depend on notification
 * permission, a notification channel or a permanent status notification.
 *
 * The class name is retained for migration/source compatibility with earlier betas. Request-related
 * methods now deliberately succeed without displaying anything. The only remaining user-visible
 * notification is a best-effort result after a locally triggered emergency action.
 */
class RequestVisibilityNotifier(private val context: Context) {
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(
                LOCAL_ACTION_CHANNEL_ID,
                "VeVak — actions locales",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Résultat facultatif d'une action locale déclenchée volontairement dans VeVak."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )

        // Channels from older betas are no longer used. Removing them prevents VeVak from leaving
        // misleading request/status notification settings behind after an upgrade.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.deleteNotificationChannel(LEGACY_REQUEST_CHANNEL_ID)
            manager.deleteNotificationChannel(LEGACY_DISCREET_REQUEST_CHANNEL_ID)
        }
        cancelActiveStatus()
    }

    /** Automatic SMS processing is independent from notifications. */
    fun notificationsAllowedForRequests(discreet: Boolean = false): Boolean = true

    /** Permanent "VeVak actif" notifications were removed after beta feedback. */
    fun syncActiveStatus(settings: com.vevak.app.model.VeVakSettings) {
        cancelActiveStatus()
    }

    /** Requests are intentionally silent. The local audit remains available inside VeVak. */
    fun showRequestReceived(discreet: Boolean = false): Boolean = true

    fun showEmergencyResult(sentCount: Int, targetCount: Int, detail: String) {
        if (!optionalNotificationsAllowed()) return
        val summary = when {
            targetCount <= 0 -> detail
            sentCount == targetCount -> "Alerte transmise à Android pour $sentCount contact${if (sentCount > 1) "s" else ""}. $detail"
            sentCount > 0 -> "$sentCount/$targetCount alertes transmises à Android. $detail"
            else -> "Aucune alerte n'a pu être transmise. $detail"
        }
        val notification = Notification.Builder(context, LOCAL_ACTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("VeVak — envoi d'urgence")
            .setContentText(summary)
            .setStyle(Notification.BigTextStyle().bigText(summary))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        manager.notify(EMERGENCY_RESULT_NOTIFICATION_ID, notification)
    }

    fun cancelActiveStatus() {
        manager.cancel(ACTIVE_NOTIFICATION_ID)
        manager.cancel(REQUEST_NOTIFICATION_ID)
    }

    private fun optionalNotificationsAllowed(): Boolean {
        ensureLocalActionChannelOnly()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(LOCAL_ACTION_CHANNEL_ID) ?: return false
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun ensureLocalActionChannelOnly() {
        if (manager.getNotificationChannel(LOCAL_ACTION_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    LOCAL_ACTION_CHANNEL_ID,
                    "VeVak — actions locales",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Résultat facultatif d'une action locale déclenchée volontairement dans VeVak."
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )
        }
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val LOCAL_ACTION_CHANNEL_ID = "vevak_local_actions"
        const val LEGACY_REQUEST_CHANNEL_ID = "vevak_requests"
        const val LEGACY_DISCREET_REQUEST_CHANNEL_ID = "vevak_requests_discreet"
        const val ACTIVE_NOTIFICATION_ID = 4101
        const val REQUEST_NOTIFICATION_ID = 4102
        const val EMERGENCY_RESULT_NOTIFICATION_ID = 4103
    }
}
