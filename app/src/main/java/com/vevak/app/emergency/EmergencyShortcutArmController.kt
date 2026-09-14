/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.emergency

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Four-second arm/cancel coordinator for the pinned shortcut and Quick Settings tile.
 *
 * A process-local job provides the expected four-second response while a one-shot, inexact
 * allow-while-idle alarm provides a process-death fallback. Android may delay that fallback under
 * system idle quotas; no exact-alarm permission or permanent service is used. The receiver consumes
 * the stored random arm id atomically, so competing deliveries in the same app process cannot both
 * send.
 */
class EmergencyShortcutArmController(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    /** Read-only display state; never extends the existing deadline. */
    fun remainingMillis(): Long = synchronized(lock) {
        stateLocked(SystemClock.elapsedRealtime()).remainingMillis
    }

    internal fun state(): EmergencyArmState = synchronized(lock) {
        stateLocked(SystemClock.elapsedRealtime())
    }

    /** An outdated Cancel tile must never arm a new alert after the deadline. */
    fun cancelIfArmed(candidateArmId: String? = null): Boolean = synchronized(lock) {
        if (candidateArmId != null && prefs().getString(KEY_ARM_ID, null) != candidateArmId) return@synchronized false
        if (!stateLocked(SystemClock.elapsedRealtime()).isCancellable) return@synchronized false
        clearArmLocked()
        true
    }

    private fun stateLocked(now: Long): EmergencyArmState {
        val prefs = prefs()
        return EmergencyArmStatePolicy.resolve(
            hasArm = !prefs.getString(KEY_ARM_ID, null).isNullOrBlank(),
            deadline = prefs.getLong(KEY_DEADLINE, 0L),
            now = now,
            armedBootCount = prefs.getInt(KEY_BOOT_COUNT, BOOT_COUNT_UNKNOWN),
            currentBootCount = currentBootCount(),
            graceMillis = GRACE_PERIOD_MILLIS
        )
    }

    fun toggle(): Result = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()

        if (stateLocked(now).isCancellable) {
            clearArmLocked()
            return@synchronized Result.Cancelled
        }

        clearArmLocked()
        val armId = UUID.randomUUID().toString()
        val deadline = now + GRACE_PERIOD_MILLIS
        val editor = prefs().edit()
            .putString(KEY_ARM_ID, armId)
            .putLong(KEY_DEADLINE, deadline)
        currentBootCount().let { bootCount ->
            if (bootCount == BOOT_COUNT_UNKNOWN) editor.remove(KEY_BOOT_COUNT)
            else editor.putInt(KEY_BOOT_COUNT, bootCount)
        }
        editor.apply()

        val alarm = alarmIntent(armId)
        alarmManager?.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            deadline,
            alarm
        )

        EmergencyFeedback(appContext).armed(armId)
        pendingJob = scope.launch {
            delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            val shouldDispatch = synchronized(lock) {
                // Keep the system fallback registered until the receiver consumes the arm. If the
                // process dies after this check but before sendBroadcast, AlarmManager still has a
                // chance to deliver the same arm id later.
                prefs().getString(KEY_ARM_ID, null) == armId
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
        val matches = prefs.getString(KEY_ARM_ID, null) == candidateArmId &&
            stateLocked(SystemClock.elapsedRealtime()).phase == EmergencyArmPhase.PENDING_SYSTEM
        if (matches) clearArmLocked()
        matches
    }

    private fun clearArmLocked() {
        val existingId = prefs().getString(KEY_ARM_ID, null)
        if (!existingId.isNullOrBlank()) {
            EmergencyFeedback(appContext).clear(existingId)
            // Look up without creating a new token just to discard it (for example after reboot).
            existingAlarmIntent(existingId)?.let { alarm ->
                alarmManager?.cancel(alarm)
                alarm.cancel()
            }
        }
        prefs().edit().clear().apply()
        pendingJob?.cancel()
        pendingJob = null
    }

    /**
     * Extras do not participate in PendingIntent identity. Give every arm a distinct data URI so an
     * already-queued PendingIntent from an older arm cannot be updated to carry the newest arm id.
     */
    private fun alarmIntent(armId: String): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        ALARM_REQUEST_CODE,
        emergencyAlarmIntent(armId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun existingAlarmIntent(armId: String): PendingIntent? = PendingIntent.getBroadcast(
        appContext,
        ALARM_REQUEST_CODE,
        emergencyAlarmIntent(armId),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )

    private fun emergencyAlarmIntent(armId: String): Intent =
        Intent(appContext, EmergencyShareReceiver::class.java).apply {
            action = EmergencyShareReceiver.ACTION_SEND_EMERGENCY_LOCATION
            data = Uri.Builder()
                .scheme("vevak")
                .authority("emergency-arm")
                .appendPath(armId)
                .build()
            putExtra(EmergencyShareReceiver.EXTRA_ARM_ID, armId)
        }

    private fun currentBootCount(): Int = Settings.Global.getInt(
        appContext.contentResolver,
        Settings.Global.BOOT_COUNT,
        BOOT_COUNT_UNKNOWN
    )

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    enum class Result { Armed, Cancelled }

    companion object {
        const val GRACE_PERIOD_MILLIS = 4_000L
        private const val PREFS = "vevak_emergency_arm"
        private const val KEY_ARM_ID = "arm_id"
        private const val KEY_DEADLINE = "deadline_elapsed_realtime"
        private const val KEY_BOOT_COUNT = "boot_count"
        private const val BOOT_COUNT_UNKNOWN = -1
        private const val ALARM_REQUEST_CODE = 41_004
        private val lock = Any()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var pendingJob: Job? = null
    }
}
