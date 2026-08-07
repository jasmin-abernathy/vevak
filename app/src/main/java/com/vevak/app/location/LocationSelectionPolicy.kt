/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.location

object LocationSelectionPolicy {
    fun acceptsCache(ageMillis: Long, maxAcceptedAgeMillis: Long): Boolean =
        ageMillis in 0..maxAcceptedAgeMillis
}
