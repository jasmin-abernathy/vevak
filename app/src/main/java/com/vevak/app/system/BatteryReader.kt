/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class BatteryReader(private val context: Context) {
    private val manager = context.getSystemService(BatteryManager::class.java)

    /**
     * Human-readable battery state suitable for an SMS reply.
     *
     * When the phone is currently charging we deliberately report that fact instead of a numeric
     * percentage. Otherwise we return the current level when Android exposes one.
     */
    fun label(): String? {
        val batteryIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0
        if (charging) return "Batterie en charge"

        return percentage()?.let { "Batterie : $it %" }
    }

    fun percentage(): Int? = manager
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 }
}
