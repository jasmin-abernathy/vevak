/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vevak.app.data.VeVakSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = VeVakSettingsRepository(appContext).current()
                val scheduler = PositionRefreshScheduler(appContext)
                if (
                    settings.startOnBoot &&
                    settings.backgroundRefreshEnabled &&
                    settings.completedOnboarding &&
                    settings.hasActiveAuthorization()
                ) {
                    // Give Android a little time to finish boot before the first best-effort refresh.
                    scheduler.scheduleNext(
                        intervalMinutes = settings.normalizedBackgroundRefreshIntervalMinutes(),
                        initialDelayMinutes = 2
                    )
                } else {
                    scheduler.cancel()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
