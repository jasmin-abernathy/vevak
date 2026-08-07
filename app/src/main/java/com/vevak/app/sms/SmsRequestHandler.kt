/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.sms

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.vevak.app.data.RuntimeStateRepository
import com.vevak.app.data.VeVakSettingsRepository
import com.vevak.app.location.LocationRequestPolicy
import com.vevak.app.location.VeVakLocationRepository
import com.vevak.app.system.BatteryReader

class SmsRequestHandler(private val context: Context) {
    private val settingsRepository = VeVakSettingsRepository(context)
    private val runtimeRepository = RuntimeStateRepository(context)
    private val phoneMatcher = PhoneNumberMatcher(context)
    private val locationRepository = VeVakLocationRepository(context)
    private val replySender = SmsReplySender(context)
    private val batteryReader = BatteryReader(context)

    suspend fun handle(intent: Intent) {
        val incoming = SmsIntentReader.read(intent) ?: return
        val settings = settingsRepository.current()
        if (!settings.isConfigured()) return
        if (!phoneMatcher.matches(incoming.sender, settings.contactPhone)) return
        if (!SmsCommandParser.matches(incoming.body, settings.triggerPhrase)) return
        if (!hasPermission(Manifest.permission.SEND_SMS)) return

        val allowed = runtimeRepository.tryAcquire(
            nowMillis = System.currentTimeMillis(),
            minimumIntervalMillis = settings.minRequestIntervalSeconds * 1_000L
        )
        if (!allowed) return

        val location = if (canReadLocationInBackground()) {
            locationRepository.fetchBestLocation(
                LocationRequestPolicy(
                    maxAcceptedCacheAgeMillis = settings.maxCachedLocationAgeSeconds * 1_000L,
                    currentLocationTimeoutMillis = settings.locationTimeoutSeconds * 1_000L,
                    allowStaleFallback = settings.allowStaleFallback
                )
            )
        } else null

        val reply = SmsReplyFormatter.format(settings, location, batteryReader.percentage())
        runCatching { replySender.send(incoming.sender, reply, incoming.subscriptionId) }
    }

    private fun canReadLocationInBackground(): Boolean {
        val foreground = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!foreground) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
