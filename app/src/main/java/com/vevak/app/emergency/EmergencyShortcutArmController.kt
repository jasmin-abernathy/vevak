/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.emergency

import android.app.AlarmManager
import android.app.PendingIntent
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
 * Four-second arm/cancel coordinator for the pinned shortcut.
 *
 * A process-local job provides the expected four-second response while a one-shot, inexact system
 * alarm provides a process-death fallback. No exact-alarm permission or permanent service is used.
 * The receiver consumes the stored random arm id atomically, so the job and alarm cannot both send.
 */
class EmergencyShortcutArmController(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    /** Read-only display state; never extends the existing deadline. */
    fun remainingMillis(): Long = synchronized(lock) {
        remainingMillisLocked(SystemClock.elapsedRealtime())
    }

    /** An outdated Cancel tile must never arm a new alert after the deadline. */
    fun cancelIfArmed(): Boolean = synchronized(lock) {
        if (remainingMillisLocked(SystemClock.elapsedRealtime()) <= 0L) return@synchronized false
        clearArmLocked()
        true
    }

    private fun remainingMillisLocked(now: Long): Long {
        val prefs = prefs()
        if (prefs.getString(KEY_ARM_ID, null).isNullOrBlank()) return 0L
        return (prefs.getLong(KEY_DEADLINE, 0L) - now)
            .takeIf { it in 1L..GRACE_PERIOD_MILLIS } ?: 0L
    }

    fun toggle(): Result = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()

        // elapsedRealtime() resets after reboot. Treat only a deadline inside the current four-second
        // window as armed; an old persisted deadline must never turn the first post-reboot tap into
        // a phantom cancellation.
        if (remainingMillisLocked(now) > 0L) {
            clearArmLocked()
            return@synchronized Result.Cancelled
        }

        clearArmLocked()
        val armId = UUID.randomUUID().toString()
        val deadline = now + GRACE_PERIOD_MILLIS
        prefs().edit()
            .putString(KEY_ARM_ID, armId)
            .putLong(KEY_DEADLINE, deadline)
            .apply()

        val alarm = alarmIntent(armId)
        alarmManager?.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, alarm)

        pendingJob = scope.launch {
            delay(GRACE_PERIOD_MILLIS)
            val shouldDispatch = synchronized(lock) {
                val stillArmed = prefs().getString(KEY_ARM_ID, null) == armId
                if (stillArmed) alarmManager?.cancel(alarm)
                stillArmed
            }
            if (shouldDispatch) {
                appContext.sendBroadcast(
                    Intent(appContext, EmergencyShareReceiver::class.java).apply {
                        action = EmergencyShareReceiver.ACTION_SEND_EMERGENCY_LOCATION
                        putExtra(EmergencyShareReceiver.EXTRA_ARM_ID, armId)
                    }
                )
            }
        }
        Result.Armed
    }

    /** Called by the private receiver; returns true exactly once for the current arm. */
    fun consumeIfArmed(candidateArmId: String?): Boolean = synchronized(lock) {
        if (candidateArmId.isNullOrBlank()) return@synchronized false
        val prefs = prefs()
        val matches = prefs.getString(KEY_ARM_ID, null) == candidateArmId
        if (matches) clearArmLocked()
        matches
    }

    private fun clearArmLocked() {
        val existingId = prefs().getString(KEY_ARM_ID, null)
        if (!existingId.isNullOrBlank()) alarmManager?.cancel(alarmIntent(existingId))
        prefs().edit().clear().apply()
        pendingJob?.cancel()
        pendingJob = null
    }

    private fun alarmIntent(armId: String): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        ALARM_REQUEST_CODE,
        Intent(appContext, EmergencyShareReceiver::class.java).apply {
            action = EmergencyShareReceiver.ACTION_SEND_EMERGENCY_LOCATION
            putExtra(EmergencyShareReceiver.EXTRA_ARM_ID, armId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    enum class Result { Armed, Cancelled }

    companion object {
        const val GRACE_PERIOD_MILLIS = 4_000L
        private const val PREFS = "vevak_emergency_arm"
        private const val KEY_ARM_ID = "arm_id"
        private const val KEY_DEADLINE = "deadline_elapsed_realtime"
        private const val ALARM_REQUEST_CODE = 41_004
        private val lock = Any()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var pendingJob: Job? = null
    }
}
