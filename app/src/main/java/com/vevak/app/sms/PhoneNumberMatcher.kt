/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.sms

import android.content.Context
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager

class PhoneNumberMatcher(private val context: Context) {
    @Suppress("DEPRECATION")
    fun matches(received: String?, trusted: String): Boolean {
        if (received.isNullOrBlank() || trusted.isBlank()) return false
        val country = countryIso()
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PhoneNumberUtils.areSamePhoneNumber(received, trusted, country)
            } else {
                PhoneNumberUtils.compare(received, trusted)
            }
        }.getOrDefault(digits(received) == digits(trusted))
    }

    private fun countryIso(): String {
        val manager = context.getSystemService(TelephonyManager::class.java)
        return runCatching {
            manager?.networkCountryIso?.takeIf { it.length == 2 }
                ?: manager?.simCountryIso?.takeIf { it.length == 2 }
                ?: "FR"
        }.getOrDefault("FR").uppercase()
    }

    private fun digits(value: String): String = value.filter(Char::isDigit).takeLast(9)
}
