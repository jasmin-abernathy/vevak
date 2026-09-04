/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.vevak.app.model.VeVakSettings

/**
 * Schedules a single future refresh tick. The receiver explicitly schedules the following tick
 * after it runs, instead of creating a repeating alarm or permanent service.
 *
 * This keeps the feature opt-in, battery-bounded and compatible with VeVak's no-permanent-
 * notification design. Android/Doze is allowed to defer the tick: the selected interval is a target
 * freshness cadence, not a promise of exact wake-up timing.
 */
class PositionRefreshScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun sync(settings: VeVakSettings) {
        if (settings.completedOnboarding && settings.backgroundRefreshEnabled && settings.hasActiveAuthorization()) {
            scheduleNext(settings.normalizedBackgroundRefreshIntervalMinutes())
        } else {
            cancel()
        }
    }

    fun scheduleNext(intervalMinutes: Int, initialDelayMinutes: Int? = null) {
        val manager = alarmManager ?: return
        val normalized = normalizeInterval(intervalMinutes)
        val delayMinutes = initialDelayMinutes?.coerceIn(1, normalized) ?: normalized
        val triggerAt = SystemClock.elapsedRealtime() + delayMinutes * MINUTE_MILLIS
        manager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            createRefreshPendingIntent()
        )
    }

    fun cancel() {
        val existing = existingRefreshPendingIntent() ?: return
        alarmManager?.cancel(existing)
        existing.cancel()
    }

    private fun createRefreshPendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            refreshIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun existingRefreshPendingIntent(): PendingIntent? =
        PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            refreshIntent(),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

    private fun refreshIntent(): Intent =
        Intent(appContext, PositionRefreshReceiver::class.java).apply { action = ACTION_REFRESH_POSITION }

    private fun normalizeInterval(value: Int): Int = when {
        value <= 15 -> 15
        value <= 30 -> 30
        else -> 60
    }

    companion object {
        const val ACTION_REFRESH_POSITION = "com.vevak.app.action.REFRESH_LAST_POSITION"
        private const val REQUEST_CODE = 5201
        private const val MINUTE_MILLIS = 60_000L
    }
}
