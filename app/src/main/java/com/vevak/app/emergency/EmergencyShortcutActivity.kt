/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.emergency

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Invisible/translucent target for the pinned home-screen shortcut.
 *
 * First tap: arm the existing emergency SMS action for four seconds.
 * Second tap while this activity is still armed: cancel and close immediately.
 * No second tap: dispatch the same local EmergencyShareReceiver used elsewhere, then close.
 */
class EmergencyShortcutActivity : ComponentActivity() {
    private var armed = false
    private var dispatchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShortcutIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    override fun onDestroy() {
        dispatchJob?.cancel()
        dispatchJob = null
        armed = false
        super.onDestroy()
    }

    private fun handleShortcutIntent(intent: Intent) {
        if (intent.action != ACTION_TOGGLE_EMERGENCY ||
            !EmergencyShortcutManager(this).isValidToken(intent.getStringExtra(EXTRA_SHORTCUT_TOKEN))
        ) {
            finishAndRemoveTask()
            return
        }

        if (armed) {
            // A deliberate second tap during the grace period is the cancellation gesture.
            armed = false
            dispatchJob?.cancel()
            dispatchJob = null
            finishAndRemoveTask()
            return
        }

        armed = true
        dispatchJob = lifecycleScope.launch {
            delay(EMERGENCY_GRACE_PERIOD_MILLIS)
            if (armed) {
                sendBroadcast(
                    Intent(this@EmergencyShortcutActivity, EmergencyShareReceiver::class.java).apply {
                        action = EmergencyShareReceiver.ACTION_SEND_EMERGENCY_LOCATION
                    }
                )
            }
            armed = false
            dispatchJob = null
            finishAndRemoveTask()
        }
    }

    companion object {
        const val ACTION_TOGGLE_EMERGENCY = "com.vevak.app.action.TOGGLE_DISCREET_EMERGENCY"
        const val EXTRA_SHORTCUT_TOKEN = "vevak_shortcut_token"
        const val EMERGENCY_GRACE_PERIOD_MILLIS = 4_000L
    }
}
