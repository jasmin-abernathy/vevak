/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.emergency

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import kotlinx.coroutines.launch

/** One activation arms. Repeated launcher activations never cancel or extend the deadline. */
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
            lifecycleScope.launch {
                try {
                    EmergencyShortcutArmController(this@EmergencyShortcutActivity).armIfIdle()
                } catch (_: Exception) {
                    Toast.makeText(this@EmergencyShortcutActivity, "Préparation non confirmée. Vérifiez VeVak.", Toast.LENGTH_LONG).show()
                } finally {
                    finishAndRemoveTask()
                }
            }
            return
        }
        finishAndRemoveTask()
    }

    companion object {
        const val ACTION_TOGGLE_EMERGENCY = "com.vevak.app.action.TOGGLE_DISCREET_EMERGENCY"
        const val EXTRA_SHORTCUT_TOKEN = "vevak_shortcut_token"
    }
}
