/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app

import android.app.Application
import com.vevak.app.background.PositionRefreshScheduler
import com.vevak.app.data.VeVakSettingsRepository
import com.vevak.app.system.RequestVisibilityNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VeVakApplication : Application() {
    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        RequestVisibilityNotifier(this).ensureChannels()

        // Reconcile the one-shot refresh alarm whenever the process starts. This is not a location
        // acquisition by itself: it only makes sure an explicitly enabled schedule exists (or that
        // a stale schedule is cancelled after the user disabled/revoked the feature).
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                PositionRefreshScheduler(this@VeVakApplication)
                    .sync(VeVakSettingsRepository(this@VeVakApplication).current())
            }
        }
    }
}
