/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.emergency

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Tiny translucent target for the pinned home-screen shortcut.
 *
 * Each invocation validates the per-install shortcut token, toggles the four-second arm/cancel
 * coordinator, then closes immediately. Closing is important: the launcher becomes touchable again
 * straight away, so a second tap on the same icon can actually cancel an accidental first tap.
 */
class EmergencyShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShortcutIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent) {
        if (intent.action == ACTION_TOGGLE_EMERGENCY &&
            EmergencyShortcutManager(this).isValidToken(intent.getStringExtra(EXTRA_SHORTCUT_TOKEN))
        ) {
            EmergencyShortcutArmController(this).toggle()
        }
        finishAndRemoveTask()
    }

    companion object {
        const val ACTION_TOGGLE_EMERGENCY = "com.vevak.app.action.TOGGLE_DISCREET_EMERGENCY"
        const val EXTRA_SHORTCUT_TOKEN = "vevak_shortcut_token"
    }
}
