/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.diagnostics

enum class CheckState { Ok, Warning, Error }

data class ReadinessCheck(val title: String, val detail: String, val state: CheckState)

data class DiagnosticsSnapshot(
    val checks: List<ReadinessCheck>,
    val backend: String,
    val report: String,
    val locationCapabilities: LocationCapabilitySnapshot? = null
) {
    val ready: Boolean get() = checks.none { it.state == CheckState.Error }
}
