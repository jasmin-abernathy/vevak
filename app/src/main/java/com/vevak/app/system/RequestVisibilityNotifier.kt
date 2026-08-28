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
import com.vevak.app.model.VeVakSettings
import java.text.DateFormat
import java.util.Date

class RequestVisibilityNotifier(private val context: Context) {
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    STATUS_CHANNEL_ID,
                    "VeVak actif",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Indique visiblement qu'un contact est autorisé à demander une position."
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(
                    REQUEST_CHANNEL_ID,
                    "Demandes VeVak",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Prévient le propriétaire du téléphone lorsqu'une demande VeVak est reçue."
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )
        )
    }

    fun notificationsAllowedForRequests(): Boolean {
        ensureChannels()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!manager.areNotificationsEnabled()) return false
        val requestChannel = manager.getNotificationChannel(REQUEST_CHANNEL_ID) ?: return false
        return requestChannel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun syncActiveStatus(settings: VeVakSettings) {
        if (!settings.hasActiveAuthorization() || !notificationsAllowedForRequests()) {
            cancelActiveStatus()
            return
        }
        val contact = settings.contactName.ifBlank { settings.contactPhone }
        val expiry = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(settings.authorizationExpiresAtEpochMs))
        val notification = Notification.Builder(context, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("VeVak est actif")
            .setContentText("$contact peut demander votre position jusqu'au $expiry.")
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        manager.notify(ACTIVE_NOTIFICATION_ID, notification)
    }

    /**
     * Returns false if VeVak cannot make the request visible. Callers must not send a
     * location response when this returns false.
     */
    fun showRequestReceived(): Boolean {
        if (!notificationsAllowedForRequests()) return false
        return runCatching {
            val notification = Notification.Builder(context, REQUEST_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Demande VeVak reçue")
                .setContentText("Une demande de position a été reçue. Ouvrez VeVak pour voir l'historique.")
                .setContentIntent(openAppIntent())
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .build()
            manager.notify(REQUEST_NOTIFICATION_ID, notification)
        }.isSuccess
    }

    fun cancelActiveStatus() {
        manager.cancel(ACTIVE_NOTIFICATION_ID)
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
        const val STATUS_CHANNEL_ID = "vevak_active_status"
        const val REQUEST_CHANNEL_ID = "vevak_requests"
        const val ACTIVE_NOTIFICATION_ID = 4101
        const val REQUEST_NOTIFICATION_ID = 4102
    }
}
