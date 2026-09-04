/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vevak.app.data.VeVakSettingsRepository
import com.vevak.app.location.VeVakPositionResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PositionRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PositionRefreshScheduler.ACTION_REFRESH_POSITION) return

        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = VeVakSettingsRepository(appContext).current()
                val scheduler = PositionRefreshScheduler(appContext)
                if (!settings.completedOnboarding || !settings.backgroundRefreshEnabled || !settings.hasActiveAuthorization()) {
                    scheduler.cancel()
                    return@launch
                }

                // The canonical resolver already remembers every successful coordinate-bearing
                // source. Excluding the trusted-place shortcut is deliberate: this job exists only
                // to improve the single remembered coordinate, never to build a movement history.
                runCatching {
                    VeVakPositionResolver(appContext).resolve(
                        settings = settings,
                        includeTrustedPlace = false
                    )
                }

                // Always schedule the next one-shot tick after this attempt. Android may delay it
                // under Doze/battery policies; VeVak does not use exact alarms or a foreground
                // service merely to force a cadence.
                scheduler.scheduleNext(settings.normalizedBackgroundRefreshIntervalMinutes())
            } finally {
                pending.finish()
            }
        }
    }
}
