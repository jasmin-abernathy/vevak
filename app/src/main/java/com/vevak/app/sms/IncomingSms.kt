/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.sms

data class IncomingSms(
    val sender: String,
    val body: String,
    val subscriptionId: Int?
)
