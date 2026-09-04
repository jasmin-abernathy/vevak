/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.emergency

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Process-local four-second arm/cancel coordinator for the pinned shortcut.
 *
 * The shortcut Activity closes immediately after each tap, so the launcher remains tappable and a
 * second tap can genuinely cancel the pending send. A tiny private preference stores the current
 * arm token/deadline so a restarted process never mistakes stale state for a new emergency.
 */
class EmergencyShortcutArmController(context: Context) {
    private val appContext = context.applicationContext

    fun toggle(): Result = synchronized(lock) {
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()
        val currentDeadline = prefs.getLong(KEY_DEADLINE, 0L)
        val currentArmId = prefs.getString(KEY_ARM_ID, null)

        if (!currentArmId.isNullOrBlank() && currentDeadline > now) {
            prefs.edit().clear().apply()
            pendingJob?.cancel()
            pendingJob = null
            return@synchronized Result.Cancelled
        }

        // Remove stale state before arming a fresh sequence.
        prefs.edit().clear().apply()
        pendingJob?.cancel()

        val armId = UUID.randomUUID().toString()
        val deadline = now + GRACE_PERIOD_MILLIS
        prefs.edit()
            .putString(KEY_ARM_ID, armId)
            .putLong(KEY_DEADLINE, deadline)
            .apply()

        pendingJob = scope.launch {
            delay(GRACE_PERIOD_MILLIS)
            val shouldSend = synchronized(lock) {
                val latest = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val stillArmed = latest.getString(KEY_ARM_ID, null) == armId &&
                    latest.getLong(KEY_DEADLINE, 0L) == deadline
                if (stillArmed) latest.edit().clear().apply()
                pendingJob = null
                stillArmed
            }
            if (shouldSend) {
                appContext.sendBroadcast(
                    Intent(appContext, EmergencyShareReceiver::class.java).apply {
                        action = EmergencyShareReceiver.ACTION_SEND_EMERGENCY_LOCATION
                    }
                )
            }
        }
        Result.Armed
    }

    enum class Result { Armed, Cancelled }

    companion object {
        const val GRACE_PERIOD_MILLIS = 4_000L
        private const val PREFS = "vevak_emergency_arm"
        private const val KEY_ARM_ID = "arm_id"
        private const val KEY_DEADLINE = "deadline_elapsed_realtime"
        private val lock = Any()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var pendingJob: Job? = null
    }
}
