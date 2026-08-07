/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.system

import android.content.Context
import android.os.BatteryManager

class BatteryReader(context: Context) {
    private val manager = context.getSystemService(BatteryManager::class.java)
    fun percentage(): Int? = manager
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 }
}
